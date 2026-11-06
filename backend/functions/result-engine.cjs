'use strict';
const {createHash,randomUUID}=require('node:crypto');
const fail=(ok,message,code='failed-precondition')=>{if(!ok){const e=new Error(message);e.code=code;throw e;}};
const id=v=>typeof v==='string'&&/^[A-Za-z0-9_.-]{1,220}$/.test(v)&&!['.','..'].includes(v);
const list=v=>Array.isArray(v)?v:[];
const str=v=>v==null?'':String(v);
function validateRows(test,marks){
 const ids=list(test.studentIds);fail(ids.length>0&&ids.length<400&&ids.every(id)&&new Set(ids).size===ids.length,'The saved test needs a valid 1–399 student roster.');
 fail(Number.isFinite(test.totalMarks)&&test.totalMarks>0&&Number.isFinite(test.passingMarks)&&test.passingMarks>=0&&test.passingMarks<=test.totalMarks,'Invalid test total or passing marks.');fail(marks.length===ids.length&&new Set(marks.map(m=>m.studentId)).size===marks.length,'Every expected student needs exactly one marks row.');
 for(const r of marks){fail(ids.includes(r.studentId)&&r.testId===test.id&&r.schoolId===test.schoolId&&r.testTeacherId===test.teacherId&&['academicSessionId','classId','sectionId','subjectId'].every(k=>r[k]===test[k]),'Marks scope does not match the saved test.');fail(['Present','Absent','Exempt'].includes(r.status),'Invalid result status.');fail(r.status==='Present'?Number.isFinite(r.marksObtained)&&r.marksObtained>=0&&r.marksObtained<=test.totalMarks:r.marksObtained==null,'Marks are incomplete or outside their allowed range.');}
 return ids;
}
function authorize(user,school,role,uid,schoolId,claims={}){fail(uid&&user?.active===true&&user.portalDisabled!==true&&user.schoolId===schoolId&&user.role===role&&school&&school.portalAccessEnabled!==false,'Active school account required.','permission-denied');fail(claims.platformOwner!==true,'Use the school Admin account, not the platform Owner.','permission-denied');}
function teacherCanSubmit(profile,test,profileId){const c=profile?.academicDirectory||{},s=list(c.sessions).find(s=>s.isCurrent===true)||list(c.sessions).find(s=>s.status==='Active');return profile?.status==='Active'&&test.teacherId===profileId&&s?.id===test.academicSessionId&&list(c.teacherAssignments).some(a=>a.teacherId===profileId&&a.academicSession===s.id&&a.classId===test.classId&&a.sectionId===test.sectionId&&a.subjectId===test.subjectId);}
function createResultAction(db,{projectId,now=()=>new Date().toISOString(),uuid=randomUUID}={}){
 return async request=>{
  fail(projectId==='edunexa-portal','This service is restricted to edunexa-portal.','permission-denied');const {auth,data:d={}}=request;fail(auth?.uid,'Sign in again.','unauthenticated');
  fail(id(d.schoolId)&&id(d.testId)&&id(d.operationId),'Invalid result request.','invalid-argument');fail(['submit','return','lock','publish','unlock'].includes(d.action),'Invalid workflow action.','invalid-argument');
  for(const k of ['expectedUpdatedAt','expectedMarksRevision','expectedWorkflowRevision'])fail(typeof d[k]==='string'&&d[k].length<=250,'Result revision required.','invalid-argument');const reason=str(d.reason).trim();fail(reason.length<=1000&&(!['return','unlock'].includes(d.action)||reason.length>=3),'Return/unlock requires a reason (3–1,000 characters).','invalid-argument');
  return db.runTransaction(async tx=>{
   const schoolRef=db.doc('schools/'+d.schoolId),testRef=schoolRef.collection('tests').doc(d.testId),flowRef=schoolRef.collection('resultWorkflow').doc(d.testId);
   const [u,s,t,w]=await Promise.all([tx.get(db.doc('users/'+auth.uid)),tx.get(schoolRef),tx.get(testRef),tx.get(flowRef)]),user=u.data(),school=s.data(),test=t.exists?{...t.data(),id:t.id,schoolId:d.schoolId}:null,flow=w.data()||{},role=d.action==='submit'?'teacher':'admin';
   authorize(user,school,role,auth.uid,d.schoolId,auth.token);fail(test&&test.status!=='Cancelled','Test is missing or cancelled.');
   if(role==='teacher'){fail(id(user.profileId),'Teacher linkage missing.','permission-denied');const p=await tx.get(schoolRef.collection('teachers').doc(user.profileId));fail(teacherCanSubmit(p.data(),test,user.profileId)&&(!p.data().authUid||p.data().authUid===auth.uid),'Only the current assigned Teacher may submit.','permission-denied');}
   if(flow.lastOperationId===d.operationId){fail(flow.lastActorUid===auth.uid&&flow.lastAction===d.action&&flow.lastReason===reason&&flow.lastExpectedMarksRevision===d.expectedMarksRevision&&flow.lastExpectedUpdatedAt===d.expectedUpdatedAt,'This retry does not match its saved request.');return {state:flow.state,revision:flow.revision,isLocked:flow.isLocked===true,isPublished:flow.isPublished===true,replayed:true};}
   fail(str(test.updatedAt)===d.expectedUpdatedAt&&str(test.marksRevision)===d.expectedMarksRevision&&str(flow.revision)===d.expectedWorkflowRevision,'The test, marks or workflow changed. Refresh before reviewing again.','aborted');
   const marksSnap=await tx.get(schoolRef.collection('marks').where('testId','==',d.testId)),marks=marksSnap.docs.map(s=>s.data());let ids=[];
   if(['submit','lock','publish'].includes(d.action))ids=validateRows(test,marks);
   if(d.action==='submit')fail(!flow.isLocked&&!flow.isPublished,'Locked/published results cannot be submitted.');
   const digest=createHash('sha256').update(JSON.stringify({test:Object.fromEntries(['title','academicSessionId','classId','sectionId','subjectId','date','assessmentType','totalMarks','passingMarks','marksRevision'].map(k=>[k,test[k]??null])),marks:[...marks].sort((a,b)=>a.studentId.localeCompare(b.studentId)).map(r=>Object.fromEntries(['studentId','status','marksObtained','remarks'].map(k=>[k,r[k]??null])))})).digest('hex');
   if(['lock','publish'].includes(d.action))fail(flow.submittedDigest===digest&&flow.submittedMarksRevision===str(test.marksRevision)&&flow.submittedUpdatedAt===str(test.updatedAt),'The Teacher must submit this exact marks revision for review first.');
   if(d.action==='lock')fail(!flow.isLocked&&!flow.isPublished,'The result is already locked/published; use Unlock for corrections.');
   if(d.action==='return')fail(!flow.isPublished,'Use Unlock to withdraw a published result.');
   if(d.action==='publish')fail(!flow.isPublished,'This result is already published. Refresh.');
   if(d.action==='unlock')fail(flow.isLocked||flow.isPublished,'The result is already unlocked.');
   const projections=['publish','unlock'].includes(d.action)?await tx.get(schoolRef.collection('publishedResults').where('testId','==',d.testId)):null;
   fail(!projections||projections.docs.length<400,'Too many published rows for an atomic operation.');
   const students=d.action==='publish'?await Promise.all(ids.map(id=>tx.get(schoolRef.collection('students').doc(id)))):[];
   fail(students.every(p=>p.exists&&p.data().isDeleted!==true),'A test-roster student was removed. Resolve the roster before publication.');
   const time=now(),revision=uuid(),next={schoolId:d.schoolId,testId:d.testId,teacherId:test.teacherId,academicSessionId:test.academicSessionId,classId:test.classId,sectionId:test.sectionId,subjectId:test.subjectId,state:({submit:'Submitted',return:'Returned',lock:'Locked',publish:'Published',unlock:'Returned'})[d.action],isLocked:['lock','publish'].includes(d.action),isPublished:d.action==='publish',revision,lastOperationId:d.operationId,lastActorUid:auth.uid,lastAction:d.action,lastReason:reason,lastExpectedMarksRevision:d.expectedMarksRevision,lastExpectedUpdatedAt:d.expectedUpdatedAt,updatedAt:time,updatedBy:auth.uid,updatedByName:str(user.name),history:[...list(flow.history).slice(-99),{id:revision,operationId:d.operationId,action:d.action,at:time,actorUid:auth.uid,actorName:str(user.name),role,reason,marksRevision:str(test.marksRevision)}]};
   if(d.action==='submit')Object.assign(next,{submittedDigest:digest,submittedMarksRevision:str(test.marksRevision),submittedUpdatedAt:str(test.updatedAt),submittedAt:time,submittedBy:auth.uid});
   if(['return','unlock'].includes(d.action))Object.assign(next,{submittedMarksRevision:'',submittedUpdatedAt:'',publishedAt:''});
   const newKeys=new Set(d.action==='publish'?ids.map(id=>'result-'+createHash('sha256').update(d.testId+'\0'+id).digest('hex')):[]),purge=projections?projections.docs.filter(r=>!newKeys.has(r.id)):[];fail(purge.length+(d.action==='publish'?ids.length:0)+1<=500,'This result exceeds the atomic publication limit. No changes saved.');
   for(const row of purge)tx.delete(row.ref);
   if(d.action==='publish')for(const p of students){const m=marks.find(r=>r.studentId===p.id),student=p.data(),key='result-'+createHash('sha256').update(d.testId+'\0'+p.id).digest('hex');tx.set(schoolRef.collection('publishedResults').doc(key),{schoolId:d.schoolId,testId:d.testId,studentId:p.id,studentName:str(student.fullName),admissionNo:str(student.admissionNo),rollNo:str(m.rollNo||student.rollNo),testTitle:str(test.title),teacherId:test.teacherId,testTeacherId:test.teacherId,teacherName:str(test.teacherName),...Object.fromEntries(['academicSessionId','academicSession','classId','className','sectionId','section','subjectId','subject','assessmentType','boardStage','date'].map(k=>[k,str(test[k])])),totalMarks:test.totalMarks,passingMarks:test.passingMarks??0,status:m.status,marksObtained:m.marksObtained??null,remarks:str(m.remarks),publishedAt:time,publishedBy:auth.uid,publishedByName:str(user.name),sourceMarksRevision:str(test.marksRevision),workflowRevision:revision});}
   if(d.action==='publish')next.publishedAt=time;
   tx.set(flowRef,next,{merge:true});return {state:next.state,revision,isLocked:next.isLocked,isPublished:next.isPublished};
  });
 };
}
module.exports={createResultAction,validateRows,authorize,teacherCanSubmit};
