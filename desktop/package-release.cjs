// Package an existing portable EXE with an explicitly supplied local profile.
// Only portable application data is included; session credentials and caches stay local.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const version = require('./package.json').version;
const root = path.resolve(__dirname, '..');
const input = path.resolve(process.argv[2] || path.join(root, '../LibreTV-免安装版'));
const binaryName = ['静观TV.exe', '静观TV-修复版.exe', 'LibreTV-修复版.exe'].find(name => fs.existsSync(path.join(input, name))) || '静观TV.exe';
const binary = path.join(input, binaryName);
const profile = path.join(input, 'LibreTV-Data');
const out = path.join(__dirname, 'dist', 'github-release');
const staging = path.join(out, 'staging');
const appDir = path.join(staging, 'LibreTV-App');
const asset = `LibreTV-App-v${version}-Windows-x64.zip`;
const sourceAsset = `LibreTV-App-v${version}-Source.zip`;
const ignored = new Set(['LOCK', 'LOG', 'LOG.old']);
const sha = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function filesIn(dir, base = dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    if (ignored.has(entry.name) || entry.isSymbolicLink()) return [];
    const full = path.join(dir, entry.name);
    return entry.isDirectory() ? filesIn(full, base) : [path.relative(base, full)];
  }).sort();
}

function copyProfileTree(relative) {
  const from = path.join(profile, relative);
  const to = path.join(appDir, 'LibreTV-Data', relative);
  const files = filesIn(from);
  for (const name of files) {
    const target = path.join(to, name);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(path.join(from, name), target);
  }
  // Fail if the database changes during the snapshot instead of shipping a mixed copy.
  if (JSON.stringify(files) !== JSON.stringify(filesIn(from)) || files.some(name => sha(path.join(from, name)) !== sha(path.join(to, name)))) {
    throw new Error('本地数据正在变化。请关闭 静观 TV 后重新打包。');
  }
  return files.map(name => path.join(relative, name));
}

if (!fs.existsSync(binary) || !fs.existsSync(profile)) throw new Error('输入目录必须含修复版 EXE 和 LibreTV-Data。');
fs.mkdirSync(out, { recursive: true });
// Fixed generated staging directory within desktop/dist; never removes input data.
fs.rmSync(staging, { recursive: true, force: true });
fs.mkdirSync(appDir, { recursive: true });
fs.copyFileSync(binary, path.join(appDir, '静观TV.exe'));
fs.mkdirSync(path.join(appDir, 'LibreTV-Data'), { recursive: true });
const desktopConfig = path.join(profile, 'desktop.json');
const config = JSON.parse(fs.readFileSync(desktopConfig, 'utf8'));
if (!Number.isInteger(config.port) || config.port < 1024 || config.port > 65535) throw new Error('Invalid portable profile port.');
fs.copyFileSync(desktopConfig, path.join(appDir, 'LibreTV-Data/desktop.json'));
const dataFiles = [
  'desktop.json',
  ...copyProfileTree('Browser/Local Storage'),
  ...copyProfileTree('Browser/IndexedDB'),
];
fs.copyFileSync(path.join(root, 'LICENSE'), path.join(appDir, 'LICENSE.txt'));
fs.copyFileSync(path.join(root, 'sources/latest.json'), path.join(appDir, 'LibreTV-Sources.json'));
fs.writeFileSync(path.join(appDir, '使用说明.txt'), `静观 TV Windows 免安装版 ${version}\n\nWindows 和 Android 手机 / TV 通用版均已发布。本包源配置更新于 2026-10-23：7 个点播源、9 个直播源。\n适用于 Windows 10/11 x64。\n\n1. 完整解压 ZIP 到可写入的文件夹，不要在压缩包中直接运行。\n2. 双击 静观TV.exe，无需安装 Node.js、Docker 或其他运行环境。\n3. 包内 LibreTV-Data 带有发布者的源配置、设置及观看记录；保留整个文件夹即可继续使用。\n4. M3U 订阅应添加到「设置 → 源管理 → 直播源」，Apple CMS API 应添加到「点播源」。\n5. 在线内容需要联网；需要代理的地址使用你自己电脑的 Windows 系统代理配置。\n6. 关闭窗口即退出，内置服务同时停止。\n\n官方源清单\n启动时会自动获取项目维护的片源清单并合并到本地配置：新增的源自动补入，你手动停用的源保持停用，你自己添加的源不受影响，删除过的官方源不会被自动加回。拉取失败时静默降级，本地配置保持原样。该请求由本地服务发出，会跟随 Windows 系统代理。\n\n升级：先关闭旧版，仅替换 EXE，不要覆盖自己的 LibreTV-Data。\n迁移：关闭后把 EXE 和自己的 LibreTV-Data 一起复制。\n全新配置：首次运行前将随包 LibreTV-Data 移走备份，程序会重新创建数据目录。\n\n本数据包不含缓存、Cookies、日志、锁文件等运行临时文件。程序启动后会自行生成必要文件。\n程序未做代码签名，Windows 可能显示发布者未知。\n订阅列表可导入不代表每个频道均可播放，线路可用性受网络、地区及编码等因素影响。\n异常日志：LibreTV-Data/desktop.log。已有数据端口被占用时请先关闭占用程序。\n\n仓库：https://github.com/wangshaojie/LibreTV-App\n`);
fs.writeFileSync(path.join(appDir, '更新日志.txt'), `2.19.2-desktop.3\n- 静观 TV：基于 LibreTV 2.19.2 的 Windows x64 免安装桌面封装。
- 应用更名为静观 TV，窗口标题、任务栏、菜单和网页界面同步更新；EXE 更名为 静观TV.exe。
- 数据目录仍为 LibreTV-Data，覆盖升级后原有源配置、设置和观看记录继续可用，无需迁移。
- 新增：启动时自动获取项目维护的官方片源清单并合并到本地配置，新增源自动补入、手动停用的源保持停用、用户自建源不受影响、删除过的官方源不会被加回。\n- 修复服务端请求不跟随 Windows 系统代理/PAC 的问题。\n- 兼容手动重定向校验、Range、流式传输和超时取消。\n- 随本 ZIP 提供发布者的本地设置、源配置和观看记录。\n- 2026-10-23 更新源配置：7 个点播源、9 个直播源；Android 2.0.8 已提供。\n`);
fs.writeFileSync(path.join(appDir, '来源与许可.txt'), `静观 TV（LibreTV-App）是基于 LibreSpark/LibreTV 的非官方客户端。\n上游：https://github.com/LibreSpark/LibreTV\n基础提交：dd1c744c40d60ee553bd9f7eb3df55dd79a8f7dc\n桌面封装及网络适配修改日期：2026-10-17\n改名与品牌更新日期：2026-10-24\n应用及桌面封装按 AGPL-3.0-or-later 提供，完整许可证见 LICENSE.txt。\n原作者署名与上游代码保留于源码仓库及源码 ZIP。\nElectron/Chromium 等第三方组件遵循各自许可证，其许可随程序内置资源提供。\n`);
fs.writeFileSync(path.join(appDir, '源码说明.txt'), `完整源码：https://github.com/wangshaojie/LibreTV-App\n当前版本：https://github.com/wangshaojie/LibreTV-App/tree/v${version}\n对应源码 ZIP：https://github.com/wangshaojie/LibreTV-App/releases/download/v${version}/${sourceAsset}\n构建步骤见源码内 README.md 和 desktop/README.md。\nEXE 内也包含应用对应源码，可通过 静观 TV 菜单中的「开源许可与源代码」打开。\n本地运行数据单独附在 Windows ZIP 中，不写入 Git 源码历史。\n`);
const innerChecksums = filesIn(appDir).map(name => `${sha(path.join(appDir, name))}  ${name.replaceAll('\\', '/')}`).join('\n') + '\n';
fs.writeFileSync(path.join(appDir, 'SHA256.txt'), innerChecksums);

const archive = spawnSync('tar.exe', ['-a', '-cf', path.join(out, asset), '-C', staging, 'LibreTV-App'], { stdio: 'inherit' });
if (archive.status !== 0) throw new Error('Windows ZIP packaging failed.');
require('./source.cjs');
fs.copyFileSync(path.join(__dirname, 'resources/LibreTV-Portable-Source.zip'), path.join(out, sourceAsset));
const checksums = [asset, sourceAsset].map(name => `${sha(path.join(out, name))}  ${name}`).join('\n') + '\n';
fs.writeFileSync(path.join(out, 'SHA256SUMS.txt'), checksums);
const manifest = { version, asset, sourceAsset, dataFiles: dataFiles.map(name => name.replaceAll('\\', '/')), dataBytes: dataFiles.reduce((sum, name) => sum + fs.statSync(path.join(appDir, 'LibreTV-Data', name)).size, 0), exeSha256: sha(binary) };
fs.writeFileSync(path.join(out, 'package-manifest.json'), JSON.stringify(manifest, null, 2));
console.log(JSON.stringify({ asset: path.join(out, asset), sourceAsset: path.join(out, sourceAsset), dataFiles: dataFiles.length, dataBytes: manifest.dataBytes }, null, 2));
