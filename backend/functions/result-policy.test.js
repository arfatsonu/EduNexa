'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createResultAction}=require('./result-engine.cjs');
test('unauthenticated requests are rejected before any Firestore access',async()=>{const action=createResultAction({runTransaction(){throw Error('Firestore must not be touched');}},{projectId:'edunexa-portal'});await assert.rejects(action({auth:null,data:{}}),e=>e.code==='unauthenticated');});
test('actual callable SDK wrapper loads and preserves its authentication rejection',async()=>{process.env.GCLOUD_PROJECT='edunexa-portal';const {edunexaResultAction}=require('./index.js');assert.equal(typeof edunexaResultAction,'function');assert.equal(typeof edunexaResultAction.run,'function');await assert.rejects(edunexaResultAction.run({auth:null,data:{}}),e=>e.code==='unauthenticated');});
