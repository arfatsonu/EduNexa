'use strict';
// Trusted local/Cloud Shell operation. Never bundle credentials into the app or chat.
const {configureOwner} = require('./owner-provisioning');
const args = process.argv.slice(2);
const byEmail = args[0] === '--email';
const identifier = args[byEmail ? 1 : 0];
const mode = args[byEmail ? 2 : 1];
if (process.env.GOOGLE_CLOUD_PROJECT !== 'edunexa-portal' ||
    !identifier || !['grant', 'revoke'].includes(mode) || args.length !== (byEmail ? 3 : 2)) {
  console.error('Set GOOGLE_CLOUD_PROJECT=edunexa-portal; run node configure-owner.js AUTH_UID grant|revoke or node configure-owner.js --email OWNER_EMAIL grant|revoke');
  process.exit(1);
}
const {initializeApp, applicationDefault} = require('firebase-admin/app');
const {getAuth} = require('firebase-admin/auth');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
initializeApp({credential: applicationDefault(), projectId: 'edunexa-portal'});
configureOwner({projectId: process.env.GOOGLE_CLOUD_PROJECT, identifier, byEmail, mode},
  {auth: getAuth(), db: getFirestore(), serverTimestamp: () => FieldValue.serverTimestamp()})
  .then(result => console.log(`Owner ${result.mode} applied to ${result.email || result.uid}. Sign out and sign in again.`))
  .catch(error => {console.error(error.message); process.exitCode = 1;});
