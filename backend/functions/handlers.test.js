const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const policy = require('./activity-policy');
function harness(){
 const records=new Map(), sent=[];
 function snap(path){return {exists:records.has(path),data:()=>records.get(path),get:k=>records.get(path)?.[k],ref:ref(path),id:path.split('/').at(-1)}}
 function ref(path){return {path,get:async()=>snap(path),set:async data=>records.set(path,data),delete:async()=>records.delete(path)}}
 const db={
  doc:ref,
  collection:col=>({where:(field,op,value)=>({get:async()=>({docs:[...records.keys()].filter(p=>p.startsWith(col+'/')&&!p.slice(col.length+1).includes('/')&&records.get(p)[field]===value).map(snap)})})}),
  runTransaction:async fn=>{
   const writes=[];
   const value=await fn({get:async r=>snap(r.path),create:(r,d)=>writes.push(()=>{assert.ok(!records.has(r.path));records.set(r.path,d)}),set:(r,d)=>writes.push(()=>records.set(r.path,d)),delete:r=>writes.push(()=>records.delete(r.path))});
   writes.forEach(fn=>fn());return value;
  }
 };
 const exports={};
 const deps={'firebase-admin/app':{initializeApp(){}},'firebase-admin/firestore':{getFirestore:()=>db,FieldValue:{serverTimestamp:()=> 'server'}},'firebase-admin/messaging':{getMessaging:()=>({send:async m=>{sent.push(m);return 'sent'}})},'firebase-functions/v2/firestore':{onDocumentWritten:(o,fn)=>fn,onDocumentCreated:(o,fn)=>fn},'firebase-functions/v2/https':{onCall:(o,fn)=>fn,HttpsError:class extends Error{constructor(code,message){super(message);this.code=code}}},'./activity-policy':policy,'./result-engine.cjs':require('./result-engine.cjs'),'./student-credentials':require('./student-credentials')};
 vm.runInNewContext(fs.readFileSync(__dirname+'/index.js','utf8'),{require:n=>deps[n],exports,process:{env:{GCLOUD_PROJECT:'edunexa-portal'}},Date,Error,JSON});
 records.set('schools/a/students/one',{status:'Active',authUid:'uid',classId:'c',sectionId:'s',academicSessionId:'year'});
 records.set('users/uid',{active:true,role:'student',schoolId:'a',profileId:'one'});
 const event=(after,before,id='event')=>({id,params:{schoolId:'a',recordId:'row'},data:{after:{exists:!!after,data:()=>after},before:{exists:!!before,data:()=>before}}});
 const notices=()=>[...records.entries()].filter(([p])=>p.includes('/notifications/'));
 return {records,sent,exports,event,notices};
}
test('saved attendance creates in-app notice once and sends one push on retried delivery',async()=>{
 const h=harness(),row={studentId:'one',status:'Absent',classId:'c',sectionId:'s',academicSessionId:'year',attendanceDate:'2026-10-12'};
 await h.exports.registerPushDevice({auth:{uid:'uid'},data:{token:'some-long-device-token-for-testing'}});
 await h.exports.attendanceActivity(h.event(row,null));await h.exports.attendanceActivity(h.event(row,null));
 assert.equal(h.notices().length,1); const [path,notice]=h.notices()[0];
 const e={params:{schoolId:'a',notificationId:path.split('/').at(-1)},data:{data:()=>notice}};
 await h.exports.deliverStudentActivity(e);await h.exports.deliverStudentActivity(e);
 assert.equal(h.sent.length,1);assert.equal(h.sent[0].data.targetUid,'uid');assert.equal(h.sent[0].notification,undefined);
});
test('wrong school/profile and inactive student produce no delivery',async()=>{
 const h=harness(),row={schoolId:'b',studentId:'one',status:'Present',classId:'c',sectionId:'s',academicSessionId:'year'};
 await h.exports.attendanceActivity(h.event(row,null));assert.equal(h.notices().length,0);
 h.records.set('schools/a/students/one',{status:'Inactive'});
 await h.exports.attendanceActivity(h.event({...row,schoolId:'a'},null));assert.equal(h.notices().length,0);
 await assert.rejects(h.exports.registerPushDevice({auth:{uid:'uid'},data:{token:'some-long-device-token-for-testing'}}));
});
test('payment stable identity deduplicates across distinct event deliveries',async()=>{
 const h=harness(),row={studentId:'one',paymentHistory:[{id:'p1',amount:200,receiptNo:'R'}]};
 await h.exports.feePaymentActivity(h.event(row,null,'e1'));await h.exports.feePaymentActivity(h.event(row,null,'e2'));
 assert.equal(h.notices().length,1);
});
test('device token account switch replaces old binding and disabled account cannot register',async()=>{
 const h=harness(),token='some-long-device-token-for-testing';
 await h.exports.registerPushDevice({auth:{uid:'uid'},data:{token}});
 h.records.set('users/second',{active:true,role:'student',schoolId:'a',profileId:'two'});
 h.records.set('schools/a/students/two',{status:'Active',authUid:'second'});
 await h.exports.registerPushDevice({auth:{uid:'second'},data:{token}});
 assert.equal(h.records.get('pushDevices/'+policy.digest(token)).uid,'second');
 h.records.get('users/second').portalDisabled=true;
 await assert.rejects(h.exports.registerPushDevice({auth:{uid:'second'},data:{token}}));
});

test('homework feedback uses canonical session ID even when context contains a label',async()=>{
 const h=harness();
 await h.exports.homeworkCheckActivity(h.event({studentId:'one',academicSessionId:'year',context:{classId:'c',sectionId:'s',academicSession:'2026-27',subject:'Math'},record:{status:'Completed'}},null));
 assert.equal(h.notices().length,1);
});
test('homework publication notifies eligible saved recipients and excludes other sections',async()=>{
 const h=harness();
 h.records.set('schools/a/students/two',{status:'Active',classId:'c',sectionId:'other',academicSessionId:'year'});
 await h.exports.homeworkActivity(h.event({homework:'Exercise 1',studentIds:['one','two'],classId:'c',sectionId:'s',academicSessionId:'year'},null));
 assert.equal(h.notices().length,1);assert.equal(h.notices()[0][1].recipientId,'one');
});
test('student disabled after notice creation receives no phone alert',async()=>{
 const h=harness();
 await h.exports.registerPushDevice({auth:{uid:'uid'},data:{token:'some-long-device-token-for-testing'}});
 await h.exports.feePaymentActivity(h.event({studentId:'one',paymentHistory:[{id:'p1',amount:200}]},null));
 h.records.get('users/uid').portalDisabled=true;
 const [path,notice]=h.notices()[0];
 await h.exports.deliverStudentActivity({params:{schoolId:'a',notificationId:path.split('/').at(-1)},data:{data:()=>notice}});
 assert.equal(h.sent.length,0);
});
