const {test}=require('node:test');const assert=require('node:assert/strict');
const {createStudentCredentialUpdate}=require('./student-credentials');
function setup(){
 const rows=new Map(Object.entries({'users/admin':{active:true,role:'admin',schoolId:'school'},'schools/school':{portalAccessEnabled:true},'schools/school/students/pupil':{authUid:'student-uid',fullName:'Pupil'},'users/student-uid':{role:'student',schoolId:'school',profileId:'pupil',active:true}})),updates=[];
 const doc=path=>({path,get:async()=>({exists:rows.has(path),data:()=>rows.get(path)})});
 const db={doc,runTransaction:async fn=>{const changes=[];await fn({get:r=>r.get(),update:(r,p)=>changes.push([r.path,p])});for(const [path,p] of changes)rows.set(path,{...rows.get(path),...p});}};
 const auth={getUser:async()=>({customClaims:{}}),updateUser:async(uid,p)=>{updates.push({uid,...p});return {uid,email:p.email};},revokeRefreshTokens:async uid=>updates.push({revoked:uid})};
 class HttpsError extends Error{constructor(code,message){super(message);this.code=code;}}
 const run=createStudentCredentialUpdate({db,auth,serverTimestamp:()=> 'server',ErrorType:HttpsError});
 const request={auth:{uid:'admin',token:{}},data:{schoolId:'school',studentId:'pupil',email:'pupil@school.edu',password:'NewPass123'}};
 return {rows,updates,auth,run,request};
}
test('Admin updates exactly the linked Auth UID; Firestore stores email and audit but no password',async()=>{
 const {rows,updates,run,request}=setup();assert.deepEqual(await run(request),{email:'pupil@school.edu'});assert.equal(updates[0].uid,'student-uid');
 assert.equal(rows.get('users/student-uid').email,request.data.email);assert.equal(rows.get('schools/school/students/pupil').portalEmail,request.data.email);
 assert.equal(JSON.stringify([...rows]).includes('NewPass123'),false);assert.equal(rows.get('users/student-uid').role,'student');
});
test('Unauthenticated, Owner, other-school Admin and forged links cannot update Auth',async()=>{
 for(const change of [s=>s.request.auth=null,s=>s.request.auth.token.platformOwner=true,s=>s.rows.set('users/admin',{active:true,role:'admin',schoolId:'other'}),s=>s.rows.set('users/student-uid',{role:'student',schoolId:'school',profileId:'other'})]){
  const s=setup();change(s);await assert.rejects(s.run(s.request));assert.deepEqual(s.updates,[]);
 }
});
test('Blank password preserves the existing password and disabled school cannot reset it',async()=>{
 const s=setup();s.request.data.password='';await s.run(s.request);assert.equal(Object.hasOwn(s.updates[0],'password'),false);
 const t=setup();t.rows.set('schools/school',{portalAccessEnabled:false});await assert.rejects(t.run(t.request));assert.deepEqual(t.updates,[]);
});
test('Invalid input is rejected before any Auth mutation',async()=>{
 for(const data of [{password:'short'},{email:'bad'},{studentId:'../pupil'}]){const s=setup();Object.assign(s.request.data,data);await assert.rejects(s.run(s.request));assert.deepEqual(s.updates,[]);}
});
test('Partial Auth/Firestore failure reports uncertainty instead of success or pretending rollback',async()=>{
 const s=setup();let calls=0;s.auth.getUser=async()=>({customClaims:{}});s.auth.revokeRefreshTokens=async()=>{throw Error('network');};
 await assert.rejects(s.run(s.request),e=>e.code==='internal'&&e.message.includes('may already have changed'));assert.equal(s.updates.length,1);
 assert.equal(s.rows.get('users/student-uid').email,undefined);
});
