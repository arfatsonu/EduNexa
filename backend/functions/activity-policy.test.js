const {test} = require('node:test');
const assert = require('node:assert/strict');
const {activities,validStudent,activeAccount,scoped,digest} = require('./activity-policy');
const scope={classId:'c',sectionId:'s',academicSessionId:'year'};
test('all attendance statuses notify including absent and corrections',()=>{
  for(const status of ['Present','Absent','Leave','Late']) assert.equal(activities('attendance',null,{studentId:'one',status,...scope}).length,1);
  assert.equal(activities('attendance',{status:'Absent'},{studentId:'one',status:'Present'}).length,1);
});
test('attendance remarks-only resave and delete do not notify',()=>{
  assert.equal(activities('attendance',{status:'Present'},{studentId:'one',status:'Present',remarks:'changed'}).length,0);
  assert.equal(activities('attendance',{},null).length,0);
});
test('payments notify each new receipt including partial payments without duplicates',()=>{
  const p={id:'payment',amount:100,receiptNo:'R1'};
  const row={studentId:'one',paymentHistory:[p,p,{id:'p2',amount:50}]};
  assert.equal(activities('fees',{},row).length,2);
  assert.equal(activities('fees',{paymentHistory:[p]},row).length,1);
  assert.equal(activities('fees',row,row).length,0);
});
test('bill creation and fee structure updates without payment do not notify',()=>{
  assert.equal(activities('fees',{}, {studentId:'one',paidAmount:0,paymentHistory:[]}).length,0);
  assert.equal(activities('fees',{}, {studentId:'one',paymentHistory:[{id:'x',amount:0}]}).length,0);
});
test('homework assignment uses saved recipients once',()=>{
  const row={homework:'Do exercise',studentIds:['one','one','two'],...scope};
  assert.equal(activities('diaryEntries',null,row).length,2);
  assert.equal(activities('diaryEntries',row,{...row,homeworkCheckId:'check',updatedAt:'later'}).length,0);
  assert.equal(activities('diaryEntries',row,{...row,homework:'Updated'}).length,2);
});
test('diary without homework and draft/archive never send homework alerts',()=>{
  for(const status of ['Draft','Archived','Cancelled']) assert.equal(activities('diaryEntries',null,{homework:'Test',studentIds:['one'],status}).length,0);
  assert.equal(activities('diaryEntries',null,{topic:'Diary',studentIds:['one']}).length,0);
});
test('homework checking reports only a real status change',()=>{
  const row={studentId:'one',record:{status:'Incomplete'}};
  assert.equal(activities('homeworkFeedback',null,row).length,1);
  assert.equal(activities('homeworkFeedback',row,{...row,checkedAt:'changed'}).length,0);
  assert.equal(activities('homeworkFeedback',row,{...row,record:{status:'Completed'}}).length,1);
});
test('student/account authorization isolates school and profile and rejects disabled',()=>{
  const u={active:true,role:'student',schoolId:'a',profileId:'one'};
  assert.equal(activeAccount(u,'a','one'),true);
  assert.equal(activeAccount(u,'b','one'),false);
  assert.equal(activeAccount(u,'a','two'),false);
  assert.equal(activeAccount({...u,portalDisabled:true},'a','one'),false);
  assert.equal(activeAccount({...u,role:'teacher'},'a','one'),false);
  assert.equal(validStudent({status:'Active',isDeleted:true}),false);
  assert.equal(validStudent({status:'Inactive'}),false);
});
test('homework/attendance scope rejects transferred classes and sessions',()=>{
  assert.equal(scoped(scope,scope),true);
  assert.equal(scoped(scope,{...scope,academicSessionId:'old'}),false);
  assert.equal(scoped(scope,{...scope,sectionId:'other'}),false);
});
test('dedup identity isolates schools, recipients, and payment IDs',()=>{
  assert.equal(digest('a','p1'),digest('a','p1'));
  assert.notEqual(digest('a','p1'),digest('b','p1'));
  assert.notEqual(digest('a','one','p1'),digest('a','two','p1'));
});
