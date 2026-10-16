const { app, BrowserWindow, shell } = require('electron');
const path = require('node:path');
const { spawn } = require('node:child_process');
let server, win;
function createWindow() {
  win = new BrowserWindow({ width: 1440, height: 940, minWidth: 360, minHeight: 620, backgroundColor: '#0b1220', title: 'SafeBuild — RC Empire', webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true } });
  win.loadURL('http://127.0.0.1:4177');
  win.webContents.setWindowOpenHandler(({ url }) => { if (url.startsWith('https://youtube.com/') || url.startsWith('mailto:')) shell.openExternal(url); return { action: 'deny' }; });
}
app.whenReady().then(() => {
  const script = path.join(__dirname, '..', 'server', 'index.cjs');
  server = spawn(process.execPath, [script], { stdio: 'inherit', env: { ...process.env, ELECTRON_RUN_AS_NODE: '1', SAFEBUILD_PORT: '4177' } });
  const wait = setInterval(() => {
    if (server.exitCode !== null) { clearInterval(wait); app.quit(); return; }
    if (!win) { clearInterval(wait); createWindow(); }
  }, 600);
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('before-quit', () => { if (server && !server.killed) server.kill(); });