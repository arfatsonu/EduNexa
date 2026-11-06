'use strict';

const PROJECT_ID = 'edunexa-portal';

/** Trusted Admin SDK operation only. Never expose this function to the Android client. */
async function configureOwner({projectId, identifier, byEmail = false, mode}, {auth, db, serverTimestamp}) {
  if (projectId !== PROJECT_ID) throw new Error('Owner setup is restricted to edunexa-portal.');
  if (!['grant', 'revoke'].includes(mode) || typeof identifier !== 'string' || !identifier.trim()) {
    throw new Error('Provide an Auth UID or email and grant|revoke.');
  }
  const value = identifier.trim();
  const user = byEmail ? await auth.getUserByEmail(value.toLowerCase()) : await auth.getUser(value);
  const claims = {...user.customClaims};
  if (mode === 'revoke') {
    delete claims.platformOwner;
    await auth.setCustomUserClaims(user.uid, claims);
    await auth.revokeRefreshTokens(user.uid);
    return {uid: user.uid, email: user.email || '', mode};
  }
  if (user.disabled || !user.email) throw new Error('Use an enabled Firebase Auth account with an email.');
  const ref = db.doc(`users/${user.uid}`);
  // Transaction also prevents a concurrent school assignment from being overwritten.
  await db.runTransaction(async tx => {
    const profile = await tx.get(ref);
    if (profile.exists) {
      const data = profile.data();
      if (data.schoolId != null && String(data.schoolId).trim() !== '' ||
          data.profileId != null && String(data.profileId).trim() !== '') {
        throw new Error('This login is linked to a school. Use a separate Owner account; school access was preserved.');
      }
      if (!['owner', 'admin'].includes(data.role) || data.active !== true || data.portalDisabled === true) {
        throw new Error('Owner requires an active standalone owner/admin profile.');
      }
      tx.update(ref, {role: 'owner', email: user.email});
    } else {
      tx.set(ref, {role: 'owner', name: user.displayName || 'Platform Owner', email: user.email,
        active: true, portalDisabled: false, createdAt: serverTimestamp()});
    }
  });
  // A profile alone cannot grant access. If claim setup fails, retrying is safe.
  claims.platformOwner = true;
  await auth.setCustomUserClaims(user.uid, claims);
  return {uid: user.uid, email: user.email, mode};
}

module.exports = {configureOwner};
