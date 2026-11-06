const {readFileSync}=require('node:fs');
const {resolve}=require('node:path');
const {test,before,after}=require('node:test');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,updateDoc,serverTimestamp,writeBatch,query,where}=require('firebase/firestore');
let env;
const projectId='demo-edunexa-rules';
before(async()=>{
 env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8088,rules:readFileSync(resolve(__dirname,'../firestore.rules'),'utf8')}});
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();
  for(const [id,role,schoolId] of [['owner','admin','a'],['admin','admin','a'],['otherAdmin','admin','b'],['pupil','student','a'],['teacher','teacher','a']]) await setDoc(doc(db,'users',id),{role,active:true,schoolId,profileId:id});
  for(const id of ['a','b']) await setDoc(doc(db,'schools',id),{name:id,settings:{schoolName:id},portalAccessEnabled:true});
  await setDoc(doc(db,'schools/a/students/pupil'),{fullName:'Pupil'});
  await setDoc(doc(db,'schools/b/students/private'),{fullName:'Other school child'});
 });
});
after(async()=>env?.cleanup());
const as=(uid,claims={})=>env.authenticatedContext(uid,claims).firestore();
test('ordinary Admin cannot list all schools or grant Owner with a profile field',async()=>{
 const db=as('admin');await assertFails(getDocs(collection(db,'schools')));
 await assertSucceeds(updateDoc(doc(db,'users/admin'),{platformOwner:true}));
 await assertFails(getDocs(collection(db,'schools')));
});
test('Owner claim grants registry read but no cross-school child records',async()=>{
 const db=as('owner',{platformOwner:true});await assertSucceeds(getDocs(collection(db,'schools')));await assertSucceeds(getDoc(doc(db,'schools/b/academicConfig/current')));await assertFails(getDoc(doc(db,'schools/b/academicConfig/registration-private')));await assertFails(getDoc(doc(db,'schools/b/students/private')));
});
test('school Admin cannot change or delete Owner access control fields',async()=>{
 const db=as('admin');await assertFails(updateDoc(doc(db,'schools/a'),{portalAccessEnabled:false}));await assertFails(updateDoc(doc(db,'schools/a'),{accessHistory:[]}));await assertSucceeds(updateDoc(doc(db,'schools/a'),{'settings.schoolName':'Updated school'}));
});
test('Owner may suspend another school but cannot modify its settings or self-suspend',async()=>{
 const db=as('owner',{platformOwner:true});
 await assertFails(updateDoc(doc(db,'schools/b'),{'settings.schoolName':'Not allowed'}));
 await assertFails(updateDoc(doc(db,'schools/a'),{portalAccessEnabled:false,accessUpdatedBy:'owner',accessUpdatedAt:serverTimestamp(),accessHistory:[]}));
 await assertSucceeds(updateDoc(doc(db,'schools/b'),{portalAccessEnabled:false,accessUpdatedBy:'owner',accessUpdatedAt:serverTimestamp(),accessHistory:[]}));
 await assertFails(getDoc(doc(as('otherAdmin'),'schools/b/students/private')));
 await assertFails(updateDoc(doc(as('otherAdmin'),'schools/b'),{portalAccessEnabled:true}));
 await assertSucceeds(getDoc(doc(as('otherAdmin'),'schools/b')));
 await assertSucceeds(updateDoc(doc(db,'schools/b'),{portalAccessEnabled:true,accessUpdatedBy:'owner',accessUpdatedAt:serverTimestamp(),accessHistory:[]}));
 await assertSucceeds(getDoc(doc(as('otherAdmin'),'schools/b/students/private')));
});
test('student cannot forge Owner or read other-school registry',async()=>{
 await assertFails(getDocs(collection(as('pupil'),'schools')));await assertFails(getDocs(collection(as('pupil',{platformOwner:true}),'schools')));
});
test('attendance history is allowed for scoped teacher and forbidden to student or other school',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'schools/a/teachers/teacher'),{status:'Active',inchargeClassId:'c',inchargeSectionId:'s',inchargeAcademicSession:'session',activeAcademicSessionId:'session'}));
 const payload={studentId:'pupil',classId:'c',sectionId:'s',academicSessionId:'session',attendanceDate:'2026-10-20',status:'Present',lastEditedByUid:'teacher',activityHistory:[{id:'12345678-1234-1234-1234-123456789012',at:'2026-10-20T00:00:00Z',actorUid:'teacher',actorName:'Teacher',role:'teacher',action:'Attendance updated',target:'Pupil',fields:['status']}]};
 await assertSucceeds(setDoc(doc(as('teacher'),'schools/a/attendance/test'),payload));
 await assertFails(updateDoc(doc(as('teacher'),'schools/a/attendance/test'),{activityHistory:[]}));
 await assertFails(updateDoc(doc(as('teacher'),'schools/a/attendance/test'),{activityHistory:[...payload.activityHistory,{...payload.activityHistory[0],id:'22345678-1234-1234-1234-123456789012',actorUid:'owner'}]}));
 await assertFails(setDoc(doc(as('pupil'),'schools/a/attendance/forged'),payload));
 await assertFails(setDoc(doc(as('otherAdmin'),'schools/a/attendance/cross-school'),payload));
});

test('legitimate teacher update and 100-entry history rollover still succeed',async()=>{
 const db=as('teacher');
 const event=(n)=>({id:`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`,at:'2026-10-20T00:00:00Z',actorUid:'teacher',actorName:'Teacher',role:'teacher',action:'Attendance updated',target:'Pupil',fields:['status']});
 const payload={studentId:'pupil',classId:'c',sectionId:'s',academicSessionId:'session',attendanceDate:'2026-10-20',status:'Present',lastEditedByUid:'teacher',activityHistory:[event(1)]};
 await assertSucceeds(setDoc(doc(db,'schools/a/attendance/legitimate'),payload));
 await assertSucceeds(updateDoc(doc(db,'schools/a/attendance/legitimate'),{status:'Absent',lastEditedByUid:'teacher',activityHistory:[event(1),event(2)]}));
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'schools/a/attendance/rollover'),{...payload,activityHistory:Array.from({length:100},(_,i)=>event(i+1))}));
 await assertSucceeds(updateDoc(doc(db,'schools/a/attendance/rollover'),{status:'Absent',lastEditedByUid:'teacher',activityHistory:Array.from({length:100},(_,i)=>event(i+2))}));
});
test('Admin can edit a student and receive a fee with atomic history but cannot forge actor UID',async()=>{
 const db=as('admin');const event={id:'52345678-1234-1234-1234-123456789012',at:'2026-10-20T00:00:00Z',actorUid:'admin',actorName:'Admin',role:'admin',action:'Profile updated',target:'Pupil',fields:['fullName']};
 await assertSucceeds(updateDoc(doc(db,'schools/a/students/pupil'),{fullName:'Pupil updated',lastEditedByUid:'admin',activityHistory:[event]}));
 await assertSucceeds(setDoc(doc(db,'schools/a/fees/payment'),{studentId:'pupil',paidAmount:1000,lastEditedByUid:'admin',activityHistory:[{...event,fields:['paidAmount'],action:'Fee payment received'}]}));
 await assertFails(updateDoc(doc(db,'schools/a/fees/payment'),{activityHistory:[]}));
 await assertFails(setDoc(doc(db,'schools/a/fees/forged'),{lastEditedByUid:'owner',activityHistory:[{...event,actorUid:'owner'}]}));
});

function registration(db,id,uid,role='admin',classes=[],ownerUid='owner') {
 const batch=writeBatch(db);const settings={schoolName:'New School',campusName:'Main',schoolShortName:'NS'};
 batch.set(doc(db,'schools',id),{name:'New School',settings,portalAccessEnabled:true,createdByOwnerUid:ownerUid,adminUid:uid,createdAt:serverTimestamp()});
 batch.set(doc(db,'users',uid),{name:'New Principal',email:'principal@example.com',role,schoolId:id,active:true,portalDisabled:false,createdByOwnerUid:ownerUid,createdAt:serverTimestamp()});
 batch.set(doc(db,`schools/${id}/academicConfig/current`),{classes,sections:[],sessions:[],subjects:[],teacherAssignments:[],classSubjectAssignments:[],students:[],teachers:[]});
 batch.set(doc(db,`schools/${id}/academicConfig/registrationCounter`),{lastAdmissionNumber:0,updatedAt:serverTimestamp()});
 batch.set(doc(db,`schools/${id}/academicConfig/teacherRegistrationCounter`),{lastEmployeeNumber:0,updatedAt:serverTimestamp()});
 return batch;
}
test('Owner provisioning creates an empty isolated school and Admin atomically',async()=>{
 const db=as('owner',{platformOwner:true});
 await assertSucceeds(getDoc(doc(db,'users/owner-preflight-test')));
 await assertSucceeds(registration(db,'new-school','new-admin').commit());
 await assertSucceeds(getDoc(doc(as('new-admin'),'schools/new-school/academicConfig/current')));
 await assertFails(updateDoc(doc(as('new-admin'),'schools/new-school'),{createdByOwnerUid:'new-admin',adminUid:'someone-else'}));
 await assertFails(getDoc(doc(as('new-admin'),'schools/a/students/pupil')));
 await assertFails(getDoc(doc(as('admin'),'schools/new-school/academicConfig/current')));
});
test('ordinary Admin cannot provision another school or grant a cross-school Admin',async()=>{
 await assertFails(registration(as('admin'),'forged-school','forged-admin').commit());
});
test('Owner cannot seed demo classes, provision a non-Admin role or overwrite a linked account',async()=>{
 const db=as('owner',{platformOwner:true});
 await assertFails(registration(db,'demo-school','demo-admin','admin',[{id:'demo',name:'10th'}]).commit());
 await assertFails(registration(db,'bad-role','bad-user','student').commit());
 await assertFails(registration(db,'overwrite-school','admin').commit());
 const saved=await getDoc(doc(db,'schools/demo-school'));if(saved.exists())throw new Error('Failed batch partially wrote school data');
});
test('Owner school root cannot be created without the linked Admin in the same atomic write',async()=>{
 const db=as('owner',{platformOwner:true});await assertFails(setDoc(doc(db,'schools/orphan-school'),{name:'Orphan',settings:{schoolName:'Orphan',campusName:'Main',schoolShortName:'OR'},portalAccessEnabled:true,createdByOwnerUid:'owner',adminUid:'missing-admin',createdAt:serverTimestamp()}));
});


test('standalone Owner needs no school and cannot read school operational data',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'users/platform-owner'),{role:'owner',active:true,portalDisabled:false,name:'Owner'}));
 const db=as('platform-owner',{platformOwner:true});
 await assertSucceeds(getDocs(collection(db,'schools')));
 await assertSucceeds(registration(db,'standalone-school','standalone-admin','admin',[],'platform-owner').commit());
 await assertSucceeds(updateDoc(doc(db,'schools/standalone-school'),{portalAccessEnabled:false,accessUpdatedBy:'platform-owner',accessUpdatedAt:serverTimestamp(),accessHistory:[]}));
 await assertFails(getDoc(doc(as('standalone-admin'),'schools/standalone-school/academicConfig/current')));
 await assertSucceeds(getDoc(doc(db,'schools/a/academicConfig/current')));
 await assertFails(getDoc(doc(db,'schools/a/students/pupil')));
 await assertFails(getDoc(doc(as('admin'),'users/platform-owner')));
 await assertSucceeds(getDocs(query(collection(as('admin'),'users'),where('schoolId','==','a'))));
 await assertFails(getDocs(collection(as('platform-owner'),'schools')));
 await assertFails(setDoc(doc(as('admin'),'users/fake-owner'),{role:'owner',schoolId:'a',active:true}));
 await assertFails(updateDoc(doc(as('admin'),'users/admin'),{role:'owner'}));
 await assertFails(updateDoc(doc(as('platform-owner'),'users/platform-owner'),{role:'admin',schoolId:'a'}));
});

test('Owner edits audited school identity while preserving academic settings and role links',async()=>{
 const db=as('owner',{platformOwner:true});
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'schools/profile-school'),{name:'Profile School',settings:{schoolName:'Profile School',campusName:'Main',schoolShortName:'PS',brandPrimary:'#101C30',attendanceMinimumPercent:75},portalAccessEnabled:true,adminUid:'profile-admin'}));
 const ref=doc(db,'schools/profile-school');
 const audit={ownerProfileUpdatedBy:'owner',ownerProfileUpdatedAt:serverTimestamp()};
 await assertSucceeds(updateDoc(ref,{'settings.address':'New address','settings.schoolLogo':'data:image/png;base64,iVBORw0KGgoAAA==',...audit}));
 const data=(await getDoc(ref)).data();
 require('node:assert/strict').equal(data.settings.attendanceMinimumPercent,75);
 require('node:assert/strict').equal(data.adminUid,'profile-admin');
 await assertFails(updateDoc(ref,{'settings.attendanceMinimumPercent':10,...audit}));
 await assertFails(updateDoc(ref,{'settings.brandPrimary':'#FFFFFF',...audit}));
 await assertFails(updateDoc(ref,{adminUid:'hijack',...audit}));
 await assertFails(updateDoc(ref,{'settings.schoolName':'Mismatch',...audit}));
 await assertFails(updateDoc(ref,{'settings.address':'No audit'}));
 await assertFails(updateDoc(doc(as('admin'),'schools/profile-school'),{'settings.address':'Other school'}));
 await assertFails(updateDoc(doc(as('teacher'),'schools/profile-school'),{'settings.address':'Forged',...audit}));
 await assertSucceeds(updateDoc(ref,{name:'New School Name','settings.schoolName':'New School Name',...audit}));
});
test('Owner may query limited Admin metadata but never students or unfiltered users',async()=>{
 const db=as('owner',{platformOwner:true});
 const {limit}=require('firebase/firestore');
 await assertSucceeds(getDocs(query(collection(db,'users'),where('role','==','admin'),where('schoolId','==','b'),limit(20))));
 await assertFails(getDocs(query(collection(db,'users'),where('role','==','student'),limit(20))));
 await assertFails(getDocs(collection(db,'users')));
 await assertFails(getDocs(query(collection(db,'users'),where('role','==','admin'),limit(21))));
});
test('Owner Admin identity edit cannot move schools change roles disable or edit Owner',async()=>{
 const db=as('owner',{platformOwner:true});
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'users/profile-admin'),{name:'Principal',email:'old@example.com',role:'admin',schoolId:'profile-school',active:true,portalDisabled:false}));
 const ref=doc(db,'users/profile-admin'); const audit={ownerAccountUpdatedBy:'owner',ownerAccountUpdatedAt:serverTimestamp()};
 await assertSucceeds(updateDoc(ref,{name:'New Principal',email:'new@example.com',...audit}));
 await assertFails(updateDoc(ref,{role:'owner',...audit}));
 await assertFails(updateDoc(ref,{schoolId:'a',...audit}));
 await assertFails(updateDoc(ref,{active:false,...audit}));
 await assertFails(updateDoc(ref,{email:'bad-email',...audit}));
 await assertFails(updateDoc(ref,{name:'Unaudited'}));
 await assertFails(updateDoc(doc(db,'users/owner'),{name:'Owner edited',email:'owner@example.com',...audit}));
 await assertFails(updateDoc(doc(as('admin'),'users/profile-admin'),{name:'Cross school',...audit}));
});

test('school Admin cannot forge Owner audit fields',async()=>{
 const db=as('admin');
 await assertFails(updateDoc(doc(db,'schools/a'),{ownerProfileUpdatedBy:'owner',ownerProfileUpdatedAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(db,'users/admin'),{ownerAccountUpdatedBy:'owner',ownerAccountUpdatedAt:serverTimestamp()}));
});
