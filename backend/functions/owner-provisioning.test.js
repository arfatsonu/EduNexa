'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {configureOwner} = require('./owner-provisioning');
function fixture(profile, options = {}) {
  const calls = []; let data = profile;
  const user = {uid: 'owner-uid', email: 'owner@example.com', displayName: 'Owner', customClaims: {otherClaim: true}, ...options};
  const auth = {
    getUser: async id => {calls.push(['uid', id]); return user;},
    getUserByEmail: async email => {calls.push(['email', email]); return user;},
    setCustomUserClaims: async (uid, claims) => {calls.push(['claims', uid, claims]); if(options.claimFailure) throw new Error('Claim write failed');},
    revokeRefreshTokens: async uid => calls.push(['revoke', uid])
  };
  const db = {doc: path => path, runTransaction: async fn => fn({
    get: async () => ({exists: data !== undefined, data: () => data}),
    set: (ref, value) => {calls.push(['set', ref, value]); data = value;},
    update: (ref, value) => {calls.push(['update', ref, value]); data = {...data, ...value};}
  })};
  return {calls, run: request => configureOwner({projectId: 'edunexa-portal', identifier: 'owner-uid', mode: 'grant', ...request}, {auth, db, serverTimestamp: () => 'server-time'})};
}
test('creates a standalone Owner profile using the actual Auth UID, preserving other claims', async () => {
  const f = fixture(); await f.run({identifier: 'OWNER@EXAMPLE.COM', byEmail: true});
  assert.deepEqual(f.calls[0], ['email', 'owner@example.com']);
  const created = f.calls.find(x => x[0] === 'set');
  assert.equal(created[1], 'users/owner-uid'); assert.equal(created[2].role, 'owner');
  assert.equal('schoolId' in created[2], false); assert.equal('password' in created[2], false);
  assert.deepEqual(f.calls.at(-1), ['claims', 'owner-uid', {otherClaim: true, platformOwner: true}]);
});
test('school-linked Admin or Owner cannot be repurposed and no writes occur', async () => {
  for (const role of ['admin', 'owner']) {
    const f = fixture({role, active: true, schoolId: 'school-a'});
    await assert.rejects(f.run({}), /separate Owner account/);
    assert.equal(f.calls.some(x => ['set', 'update', 'claims'].includes(x[0])), false);
  }
});
test('teacher, student, inactive and disabled profiles are rejected', async () => {
  for (const profile of [{role: 'teacher', active: true}, {role: 'student', active: true}, {role: 'admin', active: false}, {role: 'owner', active: true, portalDisabled: true}]) {
    const f = fixture(profile); await assert.rejects(f.run({}));
    assert.equal(f.calls.some(x => ['set', 'update', 'claims'].includes(x[0])), false);
  }
  const disabled = fixture(undefined, {disabled: true}); await assert.rejects(disabled.run({}));
});
test('project mismatch fails before Auth lookup', async () => {
  const f = fixture(); await assert.rejects(f.run({projectId: 'another-project'})); assert.deepEqual(f.calls, []);
});
test('standalone Owner can be safely re-granted without losing profile fields', async () => {
  const f = fixture({role: 'owner', active: true, name: 'Real Name'});
  await f.run({}); assert.deepEqual(f.calls.find(x => x[0] === 'update')[2], {role: 'owner', email: 'owner@example.com'});
});
test('revocation preserves unrelated claims and revokes sessions without editing school profiles', async () => {
  const f = fixture({role: 'admin', active: true, schoolId: 'school-a'}, {customClaims: {platformOwner: true, otherClaim: true}});
  await f.run({mode: 'revoke'});
  assert.deepEqual(f.calls.find(x => x[0] === 'claims')[2], {otherClaim: true});
  assert.equal(f.calls.some(x => x[0] === 'revoke'), true);
  assert.equal(f.calls.some(x => ['set', 'update'].includes(x[0])), false);
});
test('claim write failure is reported rather than reported as successful activation', async () => {
  const f = fixture(undefined, {claimFailure: true}); await assert.rejects(f.run({}), /Claim write failed/);
});
