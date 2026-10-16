# SafeBuild hosting feature — current implementation notes

## Local HTTP hosting
- Native Android foreground service listens on port 8088 and serves files from the app-private `files/hosted-site` directory.
- Root document is `index.html`.
- Open on the same device at `http://127.0.0.1:8088/`; other devices on the same trusted Wi-Fi may use the phone's LAN IP and port 8088.
- Persistent notification provides visibility while hosting is active.
- Stop control is available through the service action; the UI integration is still pending.
- The service attempts to restore hosting after a device reboot if it had been enabled. Android background/boot restrictions vary by OS and vendor, so this must be tested on real devices.

## Important security boundaries
- Local hosting is not a public internet URL. A public URL requires a tunnel/relay provider or a server reachable from the internet. A free tier may exist, but availability, limits, terms, and reliability depend on that provider. SafeBuild does not promise a permanent free public URL.
- Public tunnel integration and its stop/revoke UI are not implemented yet. Do not expose development sites publicly until authentication, access controls, and sensitive-file exclusion are designed and tested.
- Do not run on untrusted Wi-Fi. This development server is for preview/testing, not production hosting.
- Files are served from the app-private directory; ZIP import/extraction into this directory and the UI for selecting/copying a project are still pending.
