'use strict';
function createStudentCredentialUpdate({db,auth,serverTimestamp,ErrorType=Error}) {
 const fail=(ok,code,message)=>{if(!ok)throw new ErrorType(code,message);};
 const validId=v=>typeof v==='string'&&v.length>0&&v.length<=150&&!/[\/\\]/.test(v);
 return async request=>{
  fail(!!request.auth?.uid,'unauthenticated','Sign in first.');
  fail(request.auth.token?.platformOwner!==true,'permission-denied','Use the linked school Admin account.');
  const {schoolId,studentId,email,password=''}=request.data||{};
  fail(validId(schoolId)&&validId(studentId),'invalid-argument','Invalid school or student.');
  fail(typeof email==='string'&&email===email.trim().toLowerCase()&&email.length<=254&&/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email),'invalid-argument','Enter a valid email.');
  fail(typeof password==='string'&&(password===''||password.length>=6&&password.length<=128),'invalid-argument','Password must contain 6–128 characters.');
  const adminRef=db.doc('users/'+request.auth.uid),schoolRef=db.doc('schools/'+schoolId),studentRef=db.doc('schools/'+schoolId+'/students/'+studentId);
  const verify=async(read)=>{
   const [admin,school,student]=await Promise.all([read(adminRef),read(schoolRef),read(studentRef)]);
   const a=admin.data(),s=student.data();
   fail(a?.active===true&&a.portalDisabled!==true&&a.role==='admin'&&a.schoolId===schoolId&&school.exists&&school.data().portalAccessEnabled!==false,'permission-denied','Active school Admin required.');
   fail(student.exists&&s.isDeleted!==true&&validId(s.authUid),'failed-precondition','Student login link is unavailable.');
   const userRef=db.doc('users/'+s.authUid),user=(await read(userRef)).data();
   fail(user?.schoolId===schoolId&&user.role==='student'&&user.profileId===studentId,'permission-denied','Student account linkage changed.');
   return {uid:s.authUid,userRef};
  };
  const target=await verify(r=>r.get());
  const account=await auth.getUser(target.uid);
  fail(account.customClaims?.platformOwner!==true,'permission-denied','A platform Owner cannot be edited here.');
  try {
   const saved=await auth.updateUser(target.uid,{email,...(password?{password}:{})});
   await auth.revokeRefreshTokens(target.uid);
   await db.runTransaction(async tx=>{
    const fresh=await verify(r=>tx.get(r));
    fail(fresh.uid===target.uid,'failed-precondition','Student linkage changed.');
    const audit={updatedAt:serverTimestamp(),credentialsUpdatedAt:serverTimestamp(),credentialsUpdatedBy:request.auth.uid};
    tx.update(studentRef,{portalEmail:saved.email,...audit});
    tx.update(fresh.userRef,{email:saved.email,...audit});
   });
   return {email:saved.email};
  } catch(error) {
   // Auth and Firestore have no shared atomic transaction. Never imply rollback/success.
   if(error.code==='auth/email-already-exists')throw new ErrorType('already-exists','That email belongs to another login.');
   throw new ErrorType('internal','The login update could not be fully confirmed. Check this student login and refresh before retrying; Authentication may already have changed.');
  }
 };
}
module.exports={createStudentCredentialUpdate};
