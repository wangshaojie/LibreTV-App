# 静观 TV Windows 免安装版

基于 LibreSpark/LibreTV 2.19.2，增加 Electron 桌面外壳。

桌面修订版：2.19.2-desktop.3。在 2.19.2-desktop.2 修复内置服务不跟随 Windows 系统代理（GitHub Raw 等订阅地址连接重置）的基础上，应用更名为「静观 TV」，窗口标题、菜单、网页界面和包内 EXE 同步更新，数据目录名 `LibreTV-Data` 保持不变。订阅、搜索及服务端媒体请求通过 Chromium 网络模块发出，保留原项目的地址校验与逐跳重定向检查。

## 使用

1. 将 包内直接双击的 EXE 为 `静观TV.exe`（构建产物为 `LibreTV-2.19.2-desktop.3-Windows-x64-Portable.exe`）放到有写入权限的文件夹，双击启动。
2. 无需安装 Node.js、Docker 或浏览器，也不需要管理员权限。首次启动会解包到系统临时目录，可能需要稍等。
3. 在「设置 → 源管理」中添加自己的点播源或直播源，随后搜索和播放。项目未内置影视源；网络内容需要联网，能否播放取决于数据源、网络和视频编码。
4. 关闭窗口即退出程序，同时停止内置服务。菜单支持刷新、全屏、重新登录和打开数据文件夹。

适用：Windows 10/11 x64。ARM、32 位和更老系统未提供对应版本。

## 数据和迁移

EXE 旁会自动生成 `LibreTV-Data` 文件夹，保存观看历史、设置、浏览器数据、固定本地端口和运行日志。迁移时把 EXE 与此文件夹一起复制。不要同时运行两个副本。删除此文件夹会清空本地数据。

内置服务仅监听 `127.0.0.1`，使用随机密码自动登录。默认优先使用端口 18319；首次运行遇到占用会选取空闲端口，记录于 `LibreTV-Data/desktop.json`。以后保持相同端口以保证 IndexedDB 和 localStorage 的来源不变。如端口被其他程序占用，会提示关闭占用程序，不会静默切换导致历史记录不可见。

本构建未购买代码签名证书，Windows 可能显示发布者未知。启动问题可查看 `LibreTV-Data/desktop.log`。

## 从源码重新构建

在 Windows x64 上安装 Node.js 22 或更新受支持版本（此次使用 Node.js 24.15.0）。解压源码 ZIP，打开源码根目录执行：

```powershell
npm ci
cd desktop
npm ci
npm run dist
```

需要联网下载依赖及打包工具。输出位于 `desktop/dist/LibreTV-2.19.2-desktop.3-Windows-x64-Portable.exe`。Windows 的 `tar.exe` 用于生成对应源码 ZIP。Next.js 的 standalone 输出会被复制到桌面包，随后由 Electron 自带 Node 运行，不依赖目标机器上的开发环境。

开发运行：先执行 `npm run prepare:web`，再执行 `npm start`。打包成目录：`npm run pack`。

冒烟检查：在可写入的测试文件夹执行 EXE 并附带 `--smoke-test` 参数。程序使用隐藏窗口检查页面、静态资源、自动登录、H.264 支持和设置持久化，写出 `LibreTV-Data/smoke-result.json`、`smoke-home.png` 后退出。再次运行时结果的 `page.previous` 应为 `persisted`。测试仅验证程序封装，不保证任意第三方视频源可用。

## 开源许可

LibreTV 及此封装按 AGPL-3.0-or-later 提供。完整许可证见 `LICENSE`，上游提交见 `desktop/ORIGIN.txt`。EXE 内含此版本对应的完整应用源码 ZIP，可通过「静观 TV → 开源许可与源代码」打开。分发目录也提供相同的源码 ZIP。Electron/Chromium 及其他第三方依赖沿用各自许可证。
