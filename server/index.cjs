const express = require('express');
const helmet = require('helmet');
const multer = require('multer');
const AdmZip = require('adm-zip');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');

const app = express();
const PORT = Number(process.env.SAFEBUILD_PORT || 4177);
const ROOT = path.resolve(__dirname, '..');
const WORK = path.join(os.tmpdir(), 'safebuild-work');
fs.mkdirSync(WORK, { recursive: true });
app.disable('x-powered-by');
app.use(helmet({ contentSecurityPolicy: false }));
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(ROOT, 'public')));
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 20 * 1024 * 1024, files: 1 } });

const safeName = (name) => path.basename(String(name || 'project').replace(/[^a-zA-Z0-9._-]/g, '_')).slice(0, 100) || 'project';
function findingsForText(file, text) {
  const findings = [];
  const add = (id, severity, title, description, fix) => findings.push({ id, file, severity, title, description, fix });
  if (/\beval\s*\(/.test(text)) add('js-eval', 'high', 'Dynamic code evaluation', 'eval() can execute attacker-controlled input if data reaches it.', 'Replace eval with a parser or explicit, allow-listed operations.');
  if (/innerHTML\s*=/.test(text)) add('dom-innerhtml', 'medium', 'Potential unsafe DOM HTML assignment', 'innerHTML can enable DOM XSS when assigned untrusted data.', 'Prefer textContent or sanitize with a trusted, context-aware sanitizer.');
  if (/Access-Control-Allow-Origin\s*[:=]\s*['"]\*['"]|cors\s*\(\s*\)/i.test(text)) add('cors-wide', 'medium', 'Potentially permissive CORS', 'Wildcard or default CORS settings may expose browser-accessible APIs.', 'Allow only the specific origins that need access; verify credentials policy.');
  if (/\bSELECT\b[\s\S]{0,120}\+\s*(req\.|request\.|params\.)/i.test(text)) add('sql-concat', 'high', 'Possible SQL string concatenation', 'Query construction may be vulnerable if user input is concatenated.', 'Use parameterized queries and validate input.');
  if (/\b(?:api[_-]?key|secret|password|token)\s*[:=]\s*['"][^'"]{8,}['"]/i.test(text)) add('possible-secret', 'low', 'Possible hardcoded credential', 'This pattern may be a placeholder or local development value; it is not blocked.', 'Advisory only: consider environment variables for production deployments.');
  if (/<form\b/i.test(text) && !/\bcsrf\b|csrf-token|xsrf/i.test(text)) add('csrf-review', 'low', 'Review form CSRF protections', 'A form was found; this static check cannot establish whether CSRF protection is needed or present elsewhere.', 'For cookie-authenticated state-changing requests, use framework CSRF protection and SameSite cookies.');
  if (/\.\.\/|\bpath\.join\s*\([^\n]*(?:req\.|request\.|params\.)/i.test(text)) add('path-traversal-review', 'medium', 'Review path construction', 'Path fragments or request-derived paths were detected; validate resolved paths against an allowed root.', 'Normalize and resolve paths, then reject paths outside the intended directory.');
  return findings;
}
function scanFiles(files) {
  const findings = [];
  for (const f of files) {
    if (!/\.(?:html?|[cm]?js|jsx|ts|tsx|php|py|json|ya?ml|conf|nginx|css)$/i.test(f.path)) continue;
    if (f.content.length > 1_000_000) continue;
    findings.push(...findingsForText(f.path, f.content));
  }
  return findings;
}
app.get('/api/health', (_req, res) => res.json({ ok: true, name: 'SafeBuild', version: '1.0.0', local: true }));
app.post('/api/scan-url', async (req, res) => {
  try {
    const raw = String(req.body?.url || '');
    const url = new URL(raw);
    if (!['http:', 'https:'].includes(url.protocol)) return res.status(400).json({ error: 'Only HTTP/HTTPS URLs are supported.' });
    if (['localhost', '127.0.0.1', '::1'].includes(url.hostname) || url.hostname.endsWith('.local')) return res.status(400).json({ error: 'Loopback and local hostnames are not supported by the URL scanner.' });
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    let response;
    try { response = await fetch(url, { method: 'GET', redirect: 'manual', signal: controller.signal, headers: { 'User-Agent': 'SafeBuild-Local-Auditor/1.0' } }); }
    finally { clearTimeout(timeout); }
    const h = response.headers;
    const checks = [
      ['content-security-policy', 'Content-Security-Policy', 'medium', 'Add a carefully tested CSP compatible with the site.'],
      ['strict-transport-security', 'Strict-Transport-Security', 'medium', 'Enable HSTS only after HTTPS is confirmed across the domain.'],
      ['x-content-type-options', 'X-Content-Type-Options', 'low', 'Set X-Content-Type-Options: nosniff.'],
      ['referrer-policy', 'Referrer-Policy', 'low', 'Set a privacy-conscious Referrer-Policy.'],
      ['permissions-policy', 'Permissions-Policy', 'low', 'Disable browser capabilities the site does not need.']
    ];
    const findings = checks.filter(([, key]) => !h.has(key)).map(([id, key, severity, fix]) => ({ id, file: url.hostname, severity, title: 'Missing ' + key, description: 'The response did not include this security header. Some headers may be set on other routes.', fix }));
    res.json({ url: url.href, status: response.status, headers: Object.fromEntries([...h.entries()].filter(([k]) => /security|content-type|referrer|permissions|x-frame/i.test(k))), findings, note: 'Header checks are a limited remote audit, not a full penetration test. Redirects are not followed.' });
  } catch (e) {
    res.status(400).json({ error: e.name === 'AbortError' ? 'The request timed out.' : 'Could not audit URL. Check the address and network connection.' });
  }
});
app.post('/api/scan-file', upload.single('project'), (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'Choose a ZIP or supported source file.' });
    const name = safeName(req.file.originalname);
    const files = [];
    if (name.toLowerCase().endsWith('.zip')) {
      const zip = new AdmZip(req.file.buffer);
      const entries = zip.getEntries();
      if (entries.length > 1000) return res.status(413).json({ error: 'ZIP contains too many entries (limit 1000).' });
      let total = 0;
      for (const entry of entries) {
        if (entry.isDirectory) continue;
        const normalized = path.posix.normalize(entry.entryName.replace(/\\/g, '/'));
        if (normalized.startsWith('../') || normalized === '..' || path.posix.isAbsolute(normalized)) continue;
        total += entry.header.size || 0;
        if (total > 50 * 1024 * 1024) return res.status(413).json({ error: 'Expanded ZIP exceeds 50 MB.' });
        if ((entry.header.size || 0) > 1_000_000) continue;
        if (/\.(?:html?|[cm]?js|jsx|ts|tsx|php|py|json|ya?ml|conf|css)$/i.test(normalized)) {
          files.push({ path: normalized, content: entry.getData().toString('utf8') });
        }
      }
    } else if (/\.(?:html?|[cm]?js|jsx|ts|tsx|php|py|json|ya?ml|conf|css)$/i.test(name)) {
      files.push({ path: name, content: req.file.buffer.toString('utf8') });
    } else return res.status(400).json({ error: 'Supported files: ZIP, HTML, JS/TS, PHP, Python, JSON, YAML, CSS.' });
    const findings = scanFiles(files);
    const id = crypto.randomUUID();
    const dir = path.join(WORK, id);
    fs.mkdirSync(dir, { recursive: true });
    for (const f of files) {
      const out = path.resolve(dir, f.path);
      if (!out.startsWith(dir + path.sep)) continue;
      fs.mkdirSync(path.dirname(out), { recursive: true });
      fs.writeFileSync(out, f.content, { mode: 0o600 });
    }
    res.json({ id, fileCount: files.length, findings, summary: { high: findings.filter(x => x.severity === 'high').length, medium: findings.filter(x => x.severity === 'medium').length, low: findings.filter(x => x.severity === 'low').length }, note: 'Static heuristic scan only. Findings require manual verification; binary media is not executed or altered.' });
  } catch (e) { res.status(400).json({ error: 'Could not safely read this archive or file.' }); }
});
app.post('/api/export-config', (req, res) => {
  const platform = String(req.body?.platform || 'nginx');
  const allowed = ['cloudflare', 'vercel', 'firebase', 'nginx'];
  if (!allowed.includes(platform)) return res.status(400).json({ error: 'Unsupported hosting platform.' });
  const configs = {
    cloudflare: '/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: strict-origin-when-cross-origin\n  X-Frame-Options: DENY\n',
    vercel: JSON.stringify({ headers: [{ source: '/(.*)', headers: [{ key: 'X-Content-Type-Options', value: 'nosniff' }, { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' }, { key: 'X-Frame-Options', value: 'DENY' }] }] }, null, 2),
    firebase: JSON.stringify({ hosting: { headers: [{ source: '**', headers: [{ key: 'X-Content-Type-Options', value: 'nosniff' }, { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' }, { key: 'X-Frame-Options', value: 'DENY' }] }] } }, null, 2),
    nginx: 'add_header X-Content-Type-Options "nosniff" always;\nadd_header Referrer-Policy "strict-origin-when-cross-origin" always;\nadd_header X-Frame-Options "DENY" always;\n'
  };
  res.json({ platform, filename: ({ cloudflare: '_headers', vercel: 'vercel.json', firebase: 'firebase.json', nginx: 'safebuild-security-headers.conf' })[platform], content: configs[platform], warning: 'Review headers against your app, especially framing, CSP, authentication, and routing, before deployment.' });
});
app.use((err, _req, res, _next) => { console.error(err?.message || 'Request error'); res.status(500).json({ error: 'SafeBuild could not complete this local operation.' }); });
app.listen(PORT, '127.0.0.1', () => console.log('SafeBuild local server listening at http://127.0.0.1:' + PORT));
