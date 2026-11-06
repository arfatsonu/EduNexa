// Run only on a trusted workstation with Application Default Credentials.
// Accepts an existing Firebase Auth administrator; never stores a password.
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function validateSetup(input) {
  const keys = ['projectId', 'schoolId', 'schoolName', 'campusName', 'adminUid', 'adminName'];
  for (const key of keys) {
    if (typeof input[key] !== 'string' || !input[key].trim() || input[key] !== input[key].trim()) {
      throw new Error(`${key} is required without surrounding spaces.`);
    }
  }
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(input.projectId) || input.projectId === 'al-nisa-school-portal') {
    throw new Error('Use a separate EduNexa Firebase project.');
  }
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(input.schoolId)) throw new Error('Use a stable lowercase school ID.');
  if (input.adminUid.includes('/') || input.adminUid.length > 128) throw new Error('Invalid administrator UID.');
  if ([input.schoolName, input.campusName, input.adminName].some(v => v.length > 200)) throw new Error('Names must be at most 200 characters.');
  return input;
}

export async function provisionSchool(db, auth, input, timestamp) {
  validateSetup(input);
  const admin = await auth.getUser(input.adminUid);
  if (admin.disabled || !admin.email) throw new Error('An enabled administrator with an email is required.');
  const root = db.doc(`schools/${input.schoolId}`);
  const user = db.doc(`users/${input.adminUid}`);
  const config = root.collection('academicConfig').doc('current');
  const students = root.collection('academicConfig').doc('registrationCounter');
  const teachers = root.collection('academicConfig').doc('teacherRegistrationCounter');
  await db.runTransaction(async tx => {
    const existing = await tx.getAll(root, user, config, students, teachers);
    if (existing.some(doc => doc.exists)) throw new Error('School or administrator is already linked. No data changed.');
    tx.create(root, { name: input.schoolName, settings: { schoolName: input.schoolName,
      campusName: input.campusName }, createdAt: timestamp });
    tx.create(config, { classes: [], sections: [], sessions: [], subjects: [],
      teacherAssignments: [], classSubjectAssignments: [], students: [], teachers: [] });
    tx.create(students, { lastAdmissionNumber: 0, updatedAt: timestamp });
    tx.create(teachers, { lastEmployeeNumber: 0, updatedAt: timestamp });
    tx.create(user, { name: input.adminName, email: admin.email, role: 'admin',
      schoolId: input.schoolId, active: true, portalDisabled: false, createdAt: timestamp });
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 3) throw new Error('Usage: node provision-school.mjs school-setup.json');
    const input = validateSetup(JSON.parse(await readFile(process.argv[2], 'utf8')));
    const { initializeApp, applicationDefault } = await import('firebase-admin/app');
    const { getAuth } = await import('firebase-admin/auth');
    const { getFirestore, FieldValue } = await import('firebase-admin/firestore');
    const app = initializeApp({ credential: applicationDefault(), projectId: input.projectId });
    await provisionSchool(getFirestore(app), getAuth(app), input, FieldValue.serverTimestamp());
    console.log('School created with empty academic data. Sign in with its existing Admin account.');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
