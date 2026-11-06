'use strict';
const {createHash} = require('node:crypto');
const text = value => typeof value === 'string' ? value.trim() : '';
const digest = (...parts) => createHash('sha256').update(JSON.stringify(parts)).digest('hex');
const validStudent = s => s && s.isDeleted !== true && ['Active', 'On Leave'].includes(s.status);
const activeAccount = (u, school, profile) => u && u.active === true && u.portalDisabled !== true && u.role === 'student' && u.schoolId === school && u.profileId === profile;
const scoped = (row, student) => row.classId === student.classId && row.sectionId === student.sectionId &&
  [student.academicSessionId, student.academicSession].filter(Boolean).some(id => [row.academicSessionId, row.academicSession].includes(id));
function activities(kind, before, after) {
  if (!after) return [];
  const b = before || {}, a = after;
  const studentId = text(a.studentId);
  if (kind === 'attendance') {
    if (!studentId || !['Present','Absent','Leave','Late'].includes(a.status) || a.status === b.status) return [];
    return [{studentId, type: 'attendance', title: a.status === 'Absent' ? 'Marked absent' : 'Attendance updated',
      message: `Your attendance for ${text(a.attendanceDate || a.date)} is ${a.status}.`, scope: a}];
  }
  if (kind === 'fees') {
    if (!studentId) return [];
    const seen = new Set((b.paymentHistory || []).map(p => text(p.id)).filter(Boolean));
    const unique = new Set();
    return (Array.isArray(a.paymentHistory) ? a.paymentHistory : []).filter(p => {
      const id = text(p.id); if (!id || seen.has(id) || unique.has(id) || !(Number(p.amount) > 0)) return false;
      unique.add(id); return true;
    }).map(p => ({studentId, type: 'fee', key: text(p.id), title: 'Fee payment received',
      message: `Payment of Rs. ${Number(p.amount).toLocaleString('en-PK')} received${text(a.feeType) ? ` for ${text(a.feeType)}` : ''}. Receipt: ${text(p.receiptNo || p.receiptVoucherNo)}.`}));
  }
  if (kind === 'diaryEntries') {
    if (!text(a.homework) || ['Draft','Archived','Cancelled'].includes(a.status)) return [];
    const fields = ['homework','topic','dueDate','subject','attachment'];
    if (before && fields.every(k => JSON.stringify(b[k] ?? null) === JSON.stringify(a[k] ?? null)) && JSON.stringify(b.studentIds) === JSON.stringify(a.studentIds)) return [];
    return [...new Set((a.studentIds || []).filter(id => text(id)))].map(id => ({studentId: id, type: 'homework', scope: a,
      title: before ? 'Homework updated' : 'New homework',
      message: `${text(a.subject) || 'Homework'}: ${text(a.homework).slice(0,1000)}${text(a.dueDate) ? `\nDue: ${text(a.dueDate)}` : ''}`}));
  }
  if (kind === 'homeworkFeedback') {
    if (!studentId || !['Completed','Incomplete','Not Done','Absent'].includes(a.record?.status) || a.record.status === b.record?.status) return [];
    return [{studentId, type: 'homework', scope: {...a.context, academicSessionId:a.academicSessionId}, title: 'Homework checked',
      message: `${text(a.context?.subject) || 'Homework'}: ${a.record.status}${text(a.record?.remarks) ? `\n${text(a.record.remarks).slice(0,1000)}` : ''}.`}];
  }
  return [];
}
module.exports = {text, digest, validStudent, activeAccount, scoped, activities};
