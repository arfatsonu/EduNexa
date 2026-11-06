'use strict';
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {getMessaging} = require('firebase-admin/messaging');
const {onDocumentWritten, onDocumentCreated} = require('firebase-functions/v2/firestore');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {activities, text, digest, validStudent, activeAccount, scoped} = require('./activity-policy');
initializeApp();
const db = getFirestore();
const region = 'us-central1';
function isolation() {
  const project = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
  if (project !== 'edunexa-portal') throw new Error('EduNexa functions may only run in edunexa-portal.');
}
exports.registerPushDevice = onCall({region, maxInstances: 5}, async request => {
  isolation();
  if (!request.auth) throw new HttpsError('unauthenticated','Sign in first.');
  const token = text(request.data?.token);
  if (token.length < 20 || token.length > 4096 || /\s/.test(token)) throw new HttpsError('invalid-argument','Invalid device token.');
  const uid = request.auth.uid;
  await db.runTransaction(async tx => {
    const account = await tx.get(db.doc(`users/${uid}`));
    const user = account.data();
    if (!user || !text(user.schoolId) || !text(user.profileId) || !activeAccount(user,user.schoolId,user.profileId))
      throw new HttpsError('permission-denied','Active linked student required.');
    const profile = await tx.get(db.doc(`schools/${user.schoolId}/students/${user.profileId}`));
    if (!validStudent(profile.data()) || profile.get('authUid') !== uid) throw new HttpsError('permission-denied','Student linkage changed.');
    // One token has one owner; switching accounts replaces the previous binding.
    tx.set(db.doc(`pushDevices/${digest(token)}`), {token, uid, schoolId:user.schoolId, profileId:user.profileId, updatedAt:FieldValue.serverTimestamp()});
  });
  return {registered:true};
});
async function recordActivity(kind,event) {
  isolation();
  const after = event.data?.after.exists ? event.data.after.data() : null;
  const before = event.data?.before.exists ? event.data.before.data() : null;
  const {schoolId, recordId} = event.params;
  if (!after || (after.schoolId && after.schoolId !== schoolId)) return;
  for (const activity of activities(kind,before,after)) {
    if (activity.studentId.includes('/')) continue;
    // Payment identity remains stable across retries/rewrites. Other revisions use event ID.
    const id = `activity-${digest(schoolId,kind,recordId,activity.key || event.id,activity.studentId)}`;
    const noticeRef = db.doc(`schools/${schoolId}/notifications/${id}`);
    await db.runTransaction(async tx => {
      const student = await tx.get(db.doc(`schools/${schoolId}/students/${activity.studentId}`));
      const existing = await tx.get(noticeRef);
      if (existing.exists || !validStudent(student.data()) || (activity.scope && !scoped(activity.scope,student.data()))) return;
      const now = new Date().toISOString();
      tx.create(noticeRef, {id, schoolId, title:activity.title, message:activity.message, activityType:activity.type,
        sourceKind:kind, sourceId:recordId, sourceEventId:event.id, priority:activity.title === 'Marked absent' ? 'Important' : 'Normal',
        audience:'Individual', recipientId:activity.studentId, recipientIds:[activity.studentId],
        status:'Published', publishAt:now, expiresAt:'', createdAt:now, updatedAt:now,
        createdBy:text(after.markedBy || after.checkedBy || after.teacherName || 'School'), cloudUpdatedAt:FieldValue.serverTimestamp()});
    });
  }
}
for (const [name,kind] of Object.entries({attendanceActivity:'attendance',feePaymentActivity:'fees',homeworkActivity:'diaryEntries',homeworkCheckActivity:'homeworkFeedback'})) {
  exports[name] = onDocumentWritten({region, document:`schools/{schoolId}/${kind}/{recordId}`, retry:true, maxInstances:10}, event => recordActivity(kind,event));
}
exports.deliverStudentActivity = onDocumentCreated({region, document:'schools/{schoolId}/notifications/{notificationId}', retry:true, maxInstances:10}, async event => {
  isolation();
  const notice = event.data?.data();
  const {schoolId,notificationId} = event.params;
  if (!notice || notice.schoolId !== schoolId || notice.status !== 'Published' || !['attendance','fee','homework'].includes(notice.activityType)) return;
  const id = text(notice.recipientId);
  if (!id || id.includes('/') || notice.recipientIds?.length !== 1 || notice.recipientIds[0] !== id) return;
  const student = await db.doc(`schools/${schoolId}/students/${id}`).get();
  const uid = text(student.get('authUid'));
  if (!validStudent(student.data()) || !uid) return;
  const user = await db.doc(`users/${uid}`).get();
  if (!activeAccount(user.data(),schoolId,id)) return;
  const devices = await db.collection('pushDevices').where('uid','==',uid).get();
  for (const device of devices.docs) {
    if (device.get('schoolId') !== schoolId || device.get('profileId') !== id) continue;
    const delivery = db.doc(`pushDeliveries/${digest(schoolId,notificationId,device.id)}`);
    const claimed = await db.runTransaction(async tx => {
      const old = await tx.get(delivery); const data = old.data();
      if (data?.done) return false;
      if (data?.leaseUntil > Date.now()) throw new Error('Push delivery still leased; retry later.');
      tx.set(delivery,{leaseUntil:Date.now()+120000,done:false}); return true;
    });
    if (!claimed) continue;
    try {
      // Recheck token ownership immediately before delivery. No private content in FCM payload.
      const fresh = await device.ref.get();
      const account = await user.ref.get();
      if (fresh.get('uid') !== uid || !activeAccount(account.data(),schoolId,id)) {
        await delivery.set({done:true,skipped:true}); continue;
      }
      await getMessaging().send({token:fresh.get('token'), data:{schoolId,notificationId,targetUid:uid},
        android:{priority:'high',ttl:24*60*60*1000}});
      await delivery.set({done:true,sentAt:FieldValue.serverTimestamp()});
    } catch(error) {
      if (['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(error.code)) {
        await db.runTransaction(async tx => {
          const current = await tx.get(device.ref);
          if (current.get('uid') === uid && current.get('token') === device.get('token')) tx.delete(device.ref);
          tx.set(delivery,{done:true,invalidToken:true});
        });
      } else { await delivery.set({done:false,leaseUntil:0}); throw error; }
    }
  }
});

// Both clients share the same trusted result transaction and school isolation checks.
const {createResultAction} = require('./result-engine.cjs');
const resultAction = createResultAction(db, {projectId:process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT});
exports.edunexaResultAction = onCall({region,maxInstances:3}, async request => {
  try { return await resultAction(request); }
  catch (error) {
    if (['unauthenticated','permission-denied','invalid-argument','failed-precondition','aborted'].includes(error.code))
      throw new HttpsError(error.code,error.message);
    console.error('Result action failed',error.code || error.name);
    throw new HttpsError('internal','Result request could not be completed. Refresh to verify its current state before retrying.');
  }
});

const {createStudentCredentialUpdate} = require('./student-credentials');
exports.updateStudentCredentials = onCall({region,maxInstances:3}, async request => {
  isolation();
  return createStudentCredentialUpdate({db,auth:require('firebase-admin/auth').getAuth(),
    serverTimestamp:()=>FieldValue.serverTimestamp(),ErrorType:HttpsError})(request);
});
