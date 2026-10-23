# LibreTV-App

基于 [LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV) 的**非官方跨平台客户端**。

**Windows 免安装版已发布；Android 手机 / TV 通用原生版已实现，正在构建与验证。**

| 平台 | 状态 | 说明 |
| --- | --- | --- |
| Windows | 已发布 | Windows 10/11 x64，解压后双击运行 |
| Android | 构建验证中 | 同一 APK 兼容手机触屏和 Android TV 遥控器 |

## 下载 Windows 版

**[下载 Windows ZIP 包](https://github.com/wangshaojie/LibreTV-App/releases/download/v2.19.2-desktop.2/LibreTV-App-v2.19.2-desktop.2-Windows-x64.zip)** · [查看所有版本](https://github.com/wangshaojie/LibreTV-App/releases)

当前桌面版本：`2.19.2-desktop.2`，基于 LibreTV `2.19.2`。

ZIP 包包含免安装程序、发布者提供的 `LibreTV-Data` 本地数据快照，以及中文使用说明。数据快照保留源配置、设置和观看记录，已排除浏览器缓存、Cookies、日志和运行锁文件。

```text
LibreTV-App/
├── LibreTV.exe
├── LibreTV-Data/
├── 使用说明.txt
├── 更新日志.txt
├── 来源与许可.txt
├── 源码说明.txt
├── LICENSE.txt
└── SHA256.txt
```

## 快速开始

1. 下载 Windows ZIP 包，**完整解压**到有写入权限的文件夹。
2. 双击 `LibreTV.exe`，首次启动需要解包，请稍等片刻。
3. 无需另装 Node.js、Docker 或浏览器，也不需要管理员权限。
4. 在「设置 → 源管理」中管理自己的点播源或直播源。M3U 播放列表应添加到**直播源**；Apple CMS JSON API 应添加到**点播源**。
5. 首页用于影片搜索；顶部「直播」用于浏览 M3U 频道和分组。

如果要从空白配置开始，首次运行前将随包的 `LibreTV-Data` 移到其他文件夹备份，程序会自动创建全新的数据目录。

## Windows 版功能

- 独立桌面窗口，免安装运行。
- 保留 LibreTV 的聚合搜索、HLS 播放、直播/IPTV、源管理、历史记录和续播功能。
- 内置本地服务，启动时自动登录，关闭程序时停止服务。
- 服务端请求跟随 **Windows 系统代理/PAC**，兼容依赖代理访问的订阅地址。
- 数据保存在 EXE 旁边，方便备份和迁移。
- 支持菜单刷新、全屏、重新登录和打开数据文件夹。

程序仅监听本机回环地址 `127.0.0.1`。它不会自动安装或配置代理软件；网络访问使用当前电脑已有的代理设置。

## 数据、升级和迁移

`LibreTV-Data` 保存源配置、观看记录、设置、浏览器数据和固定本地端口。

- **备份/迁移**：关闭程序，将 EXE 与自己的 `LibreTV-Data` 一起复制。
- **升级**：关闭旧版，仅替换 EXE。不要用发布包里的示例数据覆盖你已经使用的数据目录。
- **同机运行**：避免同时启动多个副本。
- **端口占用**：已有数据目录对应的端口被占用时，关闭占用程序后重试；不要随意修改 `desktop.json`，存储来源改变后原记录可能暂时不可见。

本发布包附带的是发布者的数据快照；数据源和频道状态会随时间变化，请按自己的网络情况管理。

## 网络和播放说明

- 在线内容需要联网，订阅能导入不代表每条线路均可播放。
- 个别频道可能受地区、运营商、服务器状态或编码兼容性影响。
- 如果订阅地址依赖代理，请确保本机的系统代理和代理软件正常工作。
- 出错时可查看 `LibreTV-Data/desktop.log`；提交问题前请自行检查并隐去日志中的私人地址或令牌。
- 当前构建未做代码签名，Windows 可能显示发布者未知。

当前提供 x64 程序；Windows 7、32 位和 ARM 原生版本未提供，Android 暂无安装包。

## Android 通用版

Android 原生客户端已实现，正在完成正式签名构建和手机 / Android TV 模拟器验证。支持 M3U 直播、Apple CMS 点播搜索、原生播放、收藏、续播及配置导入导出；无需连接 Windows 或搭建服务器。源码与功能范围见 [android/README.md](android/README.md)。APK 通过验证后发布到本仓库 Releases。

## 从源码构建 Windows 版

在 Windows x64 上使用 Node.js 22 或更新的受支持版本；本次构建使用 Node.js 24.15.0。

```powershell
npm ci
cd desktop
npm ci
npm run dist
```

输出文件：

```text
desktop/dist/LibreTV-2.19.2-desktop.2-Windows-x64-Portable.exe
```

构建过程需要联网下载依赖。打包使用 Electron 和 electron-builder，源码归档使用 Windows 自带的 `tar.exe`。更多开发与验证说明见 [desktop/README.md](desktop/README.md)。

Releases 同时提供与已发布程序对应的源码 ZIP。Windows 下载包中的本地数据属于单独提供的运行数据，不纳入 Git 源码历史。

## 上游与开源许可

本项目是非官方衍生项目，原项目及原作者的贡献和署名予以保留。

- 上游项目：[LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV)
- 上游版本：`2.19.2`
- 基础提交：`dd1c744c40d60ee553bd9f7eb3df55dd79a8f7dc`
- 原项目说明：[README.upstream.md](README.upstream.md)
- 本仓库增加：Windows 便携封装、系统代理网络适配、构建/验证脚本和发布文档。
- 桌面封装修改日期：2026-10-17；仓库与 ZIP 发布文档整理日期：2026-10-20。

LibreTV 和本项目按 **AGPL-3.0-or-later** 提供，完整条款见 [LICENSE](LICENSE)。Electron、Chromium 及其他第三方组件遵循各自许可证。

本项目提供客户端和播放功能，不制作或托管影视内容。请仅使用有权访问的数据源和内容。
