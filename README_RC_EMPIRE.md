# SafeBuild v1.0 — RC Empire

**Local Autonomous AI Security Auditor & Auto-Healer**  
Created and maintained by **RC Empire**.

- Support: support.rcempire@gmail.com
- YouTube: https://youtube.com/@RCRAJAGAMER2.0

## What this build does

- Local source-pattern scanning for selected HTML, JS/TS, PHP, Python, JSON, YAML and CSS files.
- Limited remote HTTP response-header audit for a public URL.
- Generates starter security-header configuration for Cloudflare Pages, Vercel, Firebase Hosting or Nginx.
- Responsive dashboard, splash animation, day/night theme, report JSON export and installable web-app shell.
- Optional Electron desktop wrapper.

## Requirements

- Node.js 20 or newer
- npm

## Run locally

1. Download or clone the SafeBuild repository.
2. Run `npm install`.
3. Run `npm start`.
4. Open `http://127.0.0.1:4177` in your browser.
5. Optional desktop shell: run `npm run desktop` after installing dependencies.

The server binds to loopback (127.0.0.1), not all network interfaces. URL auditing needs an internet connection; project-file scanning does not.

## Scan workflow

1. Choose Local project and upload a ZIP or supported source file, or choose Live URL for a website you own or are authorized to test.
2. Select hosting target, plan tier and database context.
3. Review each finding and the suggested next step.
4. Generate the hosting configuration, inspect it, then download it.
5. Export a JSON report for your records.

## Hosting configuration output

- Cloudflare Pages: `_headers`
- Vercel: `vercel.json`
- Firebase Hosting: `firebase.json`
- Nginx: `safebuild-security-headers.conf`

These files are starter configurations, not universal drop-in fixes. Review framework compatibility, routing, embedded content, APIs, authentication, CSP, framing, and hosting-specific limits before deploying. Test on staging first.

## Security and privacy notes

- The local API listens on 127.0.0.1:4177 by default.
- ZIP processing ignores path traversal entries, caps archive entries and expanded size, and does not execute uploaded source code.
- Upload size is limited to 20 MB; expanded archive data is capped at 50 MB.
- Local source scanning uses heuristics, not a complete AST analysis or AI model. It may miss issues and produce false positives.
- URL auditing sends a network request and checks selected response headers only. It is not a penetration test and is not suitable for assessing systems without authorization.
- Hardcoded credential-like strings are advisory-only and do not block local development. Do not commit real production secrets to version control.
- Binary images and media are not executed, rewritten or claimed to be fully malware-scanned in this version.
- Uploaded project files are written to a temporary directory under the operating system's temp folder. Remove that folder when you no longer need it: `safebuild-work`.
- Do not expose this local service to untrusted networks. It is a development-stage security tool and has not undergone an independent security review.

## Fix log

- Initial scaffold: local API, file-pattern checks, limited URL header checks, hosting config generation, responsive dashboard, theme switcher and JSON report export.
- No source code is automatically rewritten in this initial build. Findings include recommended remediation steps; verify and apply fixes deliberately, then re-scan.
- Automated tests and production packaging should be added before representing this as production-ready.

## Support

For technical assistance, bug reports, or feature requests, email **support.rcempire@gmail.com**.

YouTube: [RC RAJA GAMER 2.0](https://youtube.com/@RCRAJAGAMER2.0)

© RC Empire — SafeBuild.
