const { app, BrowserWindow, Menu, dialog, session, shell, utilityProcess } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const net = require('node:net');

const portableDir = process.env.PORTABLE_EXECUTABLE_DIR || (app.isPackaged ? path.dirname(app.getPath('exe')) : __dirname);
const dataDir = path.join(portableDir, 'LibreTV-Data');
const smoke = app.commandLine.hasSwitch('smoke-test');
let mainWindow, server, origin, password, quitting = false, logFile;
let fatalShown = false;

try {
  fs.mkdirSync(dataDir, { recursive: true });
  fs.accessSync(dataDir, fs.constants.W_OK);
  app.setPath('userData', dataDir);
  fs.mkdirSync(path.join(dataDir, 'Browser'), { recursive: true });
  app.setPath('sessionData', path.join(dataDir, 'Browser'));
  logFile = path.join(dataDir, 'desktop.log');
  if (fs.existsSync(logFile) && fs.statSync(logFile).size > 2 * 1024 * 1024) fs.writeFileSync(logFile, '');
} catch (error) {
  dialog.showErrorBox('静观 TV 无法保存数据', `请将 EXE 放在可写入的文件夹后再启动。\n${error.message}`);
  app.exit(1);
}

function log(message) {
  try { fs.appendFileSync(logFile, `${new Date().toISOString()} ${message}\n`); } catch {}
}

function fail(error) {
  log(error.stack || String(error));
  if (fatalShown || quitting) return;
  fatalShown = true;
  if (smoke) {
    fs.writeFileSync(path.join(dataDir, 'smoke-result.json'), JSON.stringify({ success: false, error: error.message }));
  } else {
    dialog.showErrorBox('静观 TV 启动失败', `${error.message}\n\n日志：${logFile}`);
  }
  app.exitCode = 1;
  app.quit();
}

function probePort(port) {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.once('error', reject);
    probe.listen(port, '127.0.0.1', () => {
      const selected = probe.address().port;
      probe.close(() => resolve(selected));
    });
  });
}

async function selectPort() {
  const configPath = path.join(dataDir, 'desktop.json');
  if (fs.existsSync(configPath)) {
    const { port } = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('desktop.json 中的端口无效。');
    try { return await probePort(port); }
    catch { throw new Error(`本地端口 ${port} 已被占用，请关闭占用该端口的程序后重试。`); }
  }
  let port;
  try { port = await probePort(18319); } catch { port = await probePort(0); }
  // Keep the origin stable across restarts: IndexedDB/localStorage are origin-bound.
  fs.writeFileSync(configPath, JSON.stringify({ port }, null, 2));
  return port;
}

async function request(route, options = {}) {
  return fetch(origin + route, { ...options, signal: AbortSignal.timeout(3000) });
}

async function login() {
  const response = await request('/api/auth', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
  if (!response.ok) throw new Error(`本地自动登录失败 (${response.status})`);
  const cookie = response.headers.get('set-cookie');
  const token = cookie?.match(/(?:^|;\s*)ltv_session=([^;]+)/)?.[1];
  if (!token) throw new Error('本地服务未返回登录会话。');
  await session.defaultSession.cookies.set({
    url: origin, name: 'ltv_session', value: token,
    httpOnly: true, sameSite: 'lax', path: '/',
    expirationDate: Date.now() / 1000 + 89 * 24 * 60 * 60,
  });
}

function openExternal(url) {
  try {
    if (['https:', 'http:'].includes(new URL(url).protocol)) shell.openExternal(url).catch(log);
  } catch {}
}

function createWindow() {
  mainWindow = new BrowserWindow({
    title: '静观 TV', width: 1280, height: 840, minWidth: 860, minHeight: 620,
    show: false, backgroundColor: '#0b101a',
    icon: path.join(app.isPackaged ? process.resourcesPath : path.join(__dirname, 'resources'), 'web/public/favicon.ico'),
    webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true, webSecurity: true },
  });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => { openExternal(url); return { action: 'deny' }; });
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (new URL(url).origin !== origin) { event.preventDefault(); openExternal(url); }
  });
  mainWindow.webContents.on('will-attach-webview', event => event.preventDefault());
  mainWindow.webContents.on('render-process-gone', (_event, details) => fail(new Error(`页面进程意外退出：${details.reason}`)));
  mainWindow.once('ready-to-show', () => { if (!smoke) mainWindow.show(); });
  mainWindow.on('closed', () => { mainWindow = null; });
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: '静观 TV', submenu: [
      { label: '首页', accelerator: 'Alt+Home', click: () => mainWindow?.loadURL(origin) },
      { label: '重新登录', click: () => login().then(() => mainWindow?.loadURL(origin)).catch(fail) },
      { label: '打开数据文件夹', click: () => shell.openPath(dataDir) },
      { label: '开源许可与源代码', click: () => shell.openPath(path.join(process.resourcesPath, 'LICENSE-LibreTV.txt')).then(() => shell.openPath(path.join(process.resourcesPath, 'LibreTV-Portable-Source.zip'))) },
      { type: 'separator' }, { label: '退出', role: 'quit' },
    ] },
    { label: '编辑', submenu: [{ role: 'undo', label: '撤销' }, { role: 'redo', label: '重做' }, { type: 'separator' }, { role: 'cut', label: '剪切' }, { role: 'copy', label: '复制' }, { role: 'paste', label: '粘贴' }, { role: 'selectAll', label: '全选' }] },
    { label: '视图', submenu: [{ role: 'reload', label: '刷新' }, { role: 'resetZoom', label: '实际大小' }, { role: 'zoomIn', label: '放大' }, { role: 'zoomOut', label: '缩小' }, { role: 'togglefullscreen', label: '全屏' }] },
  ]));
  return mainWindow.loadURL(origin);
}

async function runSmokeTest() {
  const result = { success: false, desktopVersion: app.getVersion(), origin, serverPid: server.pid };
  const wc = mainWindow.webContents;
  result.page = await wc.executeJavaScript(`JSON.stringify({title: document.title, text: document.body.innerText.slice(0, 2400), h264: document.createElement('video').canPlayType('video/mp4; codecs="avc1.42E01E"'), previous: localStorage.getItem('desktop-smoke'), nodeExposed: typeof require !== 'undefined'})`);
  await wc.executeJavaScript(`localStorage.setItem('desktop-smoke', 'persisted')`);
  result.auth = await wc.executeJavaScript(`fetch('/api/auth').then(r => r.json())`);
  result.status = await wc.executeJavaScript(`fetch('/api/status').then(r => r.json())`);
  const html = await wc.executeJavaScript(`document.documentElement.outerHTML`);
  const assets = [...new Set([...html.matchAll(/(?:src|href)="([^" ]*\/_next\/[^" ]+)"/g)].map(m => m[1]))];
  result.assets = await Promise.all(assets.map(async asset => ({ asset, status: (await fetch(new URL(asset, origin))).status })));
  const source = app.commandLine.getSwitchValue('smoke-test-source');
  if (source) {
    result.playlists = [];
    const redirected = 'https://github.com/YanG-1989/m3u/raw/refs/heads/main/Gather.m3u';
    for (const url of [source, redirected]) {
      const cookies = await session.defaultSession.cookies.get({ url: origin, name: 'ltv_session' });
      const response = await fetch(origin + '/api/live/playlist?force=1&url=' + encodeURIComponent(url), {
        headers: { Cookie: 'ltv_session=' + cookies[0].value }, signal: AbortSignal.timeout(35000),
      });
      const playlist = await response.json();
      result.playlists.push({ url, status: response.status, channels: playlist.channels?.length || 0, groups: playlist.groups?.length || 0, error: playlist.error });
    }
  }
  await wc.executeJavaScript(`new Promise(r => setTimeout(r, 1500))`);
  fs.writeFileSync(path.join(dataDir, 'smoke-home.png'), (await wc.capturePage()).toPNG());
  result.success = result.auth.verified === true && !JSON.parse(result.page).nodeExposed && result.assets.length > 0 && result.assets.every(asset => asset.status === 200);
  if (source) result.success = result.success && result.playlists.every(p => p.status === 200 && p.channels > 0);
  fs.writeFileSync(path.join(dataDir, 'smoke-result.json'), JSON.stringify(result, null, 2));
  app.quit();
}

if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { if (mainWindow) { if (mainWindow.isMinimized()) mainWindow.restore(); mainWindow.show(); mainWindow.focus(); } });
  app.whenReady().then(async () => {
    const port = await selectPort();
    origin = `http://127.0.0.1:${port}`;
    password = crypto.randomBytes(32).toString('hex');
    session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => callback(permission === 'fullscreen'));
    session.defaultSession.setPermissionCheckHandler((_wc, permission) => permission === 'fullscreen');
    const webDir = path.join(app.isPackaged ? process.resourcesPath : path.join(__dirname, 'resources'), 'web');
    server = utilityProcess.fork(path.join(webDir, '..', 'server-bootstrap.cjs'), [], {
      cwd: webDir, serviceName: '静观 TV 本地服务', stdio: 'pipe',
      // A separate, nonpersistent network session avoids sharing local login
      // cookies with upstream requests, while retaining OS proxy/PAC behavior.
      partition: 'libretv-upstream',
      env: { ...process.env, NODE_ENV: 'production', HOSTNAME: '127.0.0.1', PORT: String(port), PASSWORD: password, PROXY_SECRET: crypto.randomBytes(32).toString('hex'), COOKIE_SECURE: 'false', NEXT_TELEMETRY_DISABLED: '1' },
    });
    let serverExited = false;
    server.stdout?.on('data', data => log(String(data).trimEnd()));
    server.stderr?.on('data', data => log(String(data).trimEnd()));
    server.on('exit', code => { serverExited = true; if (!quitting) fail(new Error(`本地服务意外退出 (${code})`)); });
    const deadline = Date.now() + 60000;
    let ready = false;
    while (Date.now() < deadline && !quitting && !serverExited) {
      try { ready = (await request('/api/status')).ok; if (ready) break; } catch {}
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    if (quitting) return;
    if (!ready) throw new Error('本地服务启动超时，请查看日志。');
    await login();
    await createWindow();
    log(`Ready ${origin}`);
    if (smoke) await runSmokeTest();
  }).catch(fail);
}
app.on('window-all-closed', () => app.quit());
app.on('before-quit', () => { quitting = true; if (server) server.kill(); });
process.on('uncaughtException', fail);
process.on('unhandledRejection', fail);
