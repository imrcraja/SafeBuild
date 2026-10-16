const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
test('SafeBuild includes the local server and UI', () => {
  const root = path.resolve(__dirname, '..');
  for (const f of ['server/index.cjs','public/index.html','public/app.js','README_RC_EMPIRE.md']) {
    assert.equal(fs.existsSync(path.join(root, f)), true, f + ' should exist');
  }
});
test('app does not execute uploaded source code', () => {
  const server = fs.readFileSync(path.resolve(__dirname, '../server/index.cjs'), 'utf8');
  assert.match(server, /entry\.getData\(\)\.toString\('utf8'\)/);
  assert.doesNotMatch(server, /child_process\.exec\(|eval\(uploaded/);
});