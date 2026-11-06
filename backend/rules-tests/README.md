# Firestore permission regression tests

Use Node 20+ and Java 21+. From the backend directory:

```sh
npm --prefix rules-tests ci --ignore-scripts
rules-tests/node_modules/.bin/firebase emulators:exec --config firebase.rules-test.json --only firestore --project demo-edunexa-rules 'npm --prefix rules-tests test'
```

This uses a demo project and a local Firestore emulator on port 8088. It does not
publish rules, touch live data or require Firebase credentials. Tests cover
Owner registration, school identity management, Admin metadata, access controls,
role isolation and academic-data restrictions.
