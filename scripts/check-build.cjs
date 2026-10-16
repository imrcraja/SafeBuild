const fs = require('node:fs');
const path = require('node:path');
const required = ['server/index.cjs','public/index.html','public/styles.css','public/app.js','README_RC_EMPIRE.md','electron/main.cjs'];
const missing = required.filter(f => !fs.existsSync(path.join(__dirname, '..', f)));
if (missing.length) { console.error('Missing files:', missing.join(', ')); process.exit(1); }
for (const file of ['server/index.cjs','public/app.js','electron/main.cjs']) {
  const text = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
  if (!text.trim()) { console.error('Empty source file:', file); process.exit(1); }
}
console.log('SafeBuild scaffold checks passed. This is not a security certification.');