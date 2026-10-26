<div align="center">

# 静观 TV

### 把 LibreTV 装进你的手机和电视 —— 原生客户端、免安装、数据只留在本机

[![Stars](https://img.shields.io/github/stars/wangshaojie/LibreTV-App?style=flat-square&label=Stars&color=40dec2)](https://github.com/wangshaojie/LibreTV-App/stargazers)
[![Forks](https://img.shields.io/github/forks/wangshaojie/LibreTV-App?style=flat-square&label=Forks&color=40dec2)](https://github.com/wangshaojie/LibreTV-App/network/members)
[![Release](https://img.shields.io/github/v/release/wangshaojie/LibreTV-App?style=flat-square&label=Release&color=40dec2)](https://github.com/wangshaojie/LibreTV-App/releases/latest)
[![Android](https://img.shields.io/badge/Android-5.0%2B-40dec2?style=flat-square&logo=android&logoColor=black)](https://github.com/wangshaojie/LibreTV-App/releases)
[![Windows](https://img.shields.io/badge/Windows-10%2F11%20x64-0078d4?style=flat-square&logo=windows&logoColor=white)](https://github.com/wangshaojie/LibreTV-App/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0--later-blue?style=flat-square)](LICENSE)

[**下载 Android APK**](https://github.com/wangshaojie/LibreTV-App/releases/download/android-v2.0.14/LibreTV-App-Android-v2.0.14-universal-android5.apk) · [**下载 Windows 免安装版（7z · 109 MB）**](https://github.com/wangshaojie/LibreTV-App/releases/download/v2.19.2-desktop.8/LibreTV-App-v2.19.2-desktop.8-Windows-x64.7z) · [**Windows ZIP · 158 MB**](https://github.com/wangshaojie/LibreTV-App/releases/download/v2.19.2-desktop.8/LibreTV-App-v2.19.2-desktop.8-Windows-x64.zip)

</div>

---

## 这是什么

**静观 TV** 是基于 [LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV) 的**非官方跨平台客户端**。

原版是一个需要自己用 Docker 部署的网页应用。我们把它做成了两个可以直接用的东西：

- **Windows 免安装版** —— 绿色 EXE + 数据目录，解压即用，不需要装 Node.js、Docker 或管理员权限。
- **Android 原生客户端** —— 一个 APK，手机触屏和电视遥控器都是原生手感，最低支持 Android 5.0 的老电视。

内置 **9 个点播源、9 个直播源**，开箱即用。你的片源配置、收藏和观看记录**全部保存在自己的设备上**——没有账号，没有遥测，不投放广告。

## 和原版有什么不同

| | 静观 TV | 原版 LibreTV |
| --- | --- | --- |
| **Android** | 原生 APK，手机触屏 + 电视遥控器，最低 Android 5.0 | 无官方客户端 |
| **Windows** | 绿色 EXE，解压即用 | 需自行 Docker 部署 |
| **片源更新** | 官方清单远程合并，源坏了不用手动改 | 手动编辑配置 |
| **片源体检** | Windows 与 Android 原生客户端内均可批量测活 | 仅网页端 |
| **网络** | Windows 版自动跟随系统代理 / PAC | 需自行配置代理 |
| **隐私** | 数据全在本机，无账号、无遥测 | 自托管，配置项更多 |

## 亮点

🎬 **详情页里直接换片源** —— 同一部影片在多个源都有时，详情页下方就是「片源 · N 个可用」一行，点一下即切换，线路与剧集立刻跟着换，不必退回搜索结果重选。剧集不跨源合并，避免不同源集数对不上。

🔄 **官方片源清单远程维护** —— 启动时自动拉取项目维护的源清单并与本地配置合并。**合并以你的意图为准**：新增的源自动补入，你手动停用的源保持停用，你自己添加的源完全不受影响，你删除过的官方源不会被加回。清单地址由维护者控制，普通用户只能读取。

🩺 **批量测活** —— 一次检查所有片源或所有直播频道，可随时停止、筛选异常并重测，结果保存在本机。

📺 **为老电视专门修过** —— 早期电视只有硬件解码器，切台时销毁重建解码器会导致固件 native 崩溃、整台电视「只有声音没有画面」。我们改为复用同一个播放器实例，并修复了老系统 IPv6 路由缺失导致连不上站点的问题。

🔒 **隐私干净** —— 不请求通讯录、定位、摄像头或全盘存储权限；不集成广告、统计或遥测 SDK。

## 下载

### Windows 免安装版

<div align="center">

**[⬇ 下载 Windows 7z 包](https://github.com/wangshaojie/LibreTV-App/releases/download/v2.19.2-desktop.8/LibreTV-App-v2.19.2-desktop.8-Windows-x64.7z)** · **约 109 MB** &nbsp;·&nbsp; **[⬇ 下载 Windows ZIP 包](https://github.com/wangshaojie/LibreTV-App/releases/download/v2.19.2-desktop.8/LibreTV-App-v2.19.2-desktop.8-Windows-x64.zip)** · **约 158 MB** &nbsp;·&nbsp; [查看所有版本](https://github.com/wangshaojie/LibreTV-App/releases)

</div>

版本 `2.19.2-desktop.8` · Windows 10 / 11 x64 · 解压后打开 `LibreTV-App` 文件夹，双击 `静观TV.exe` 即可运行

两个包内容完全相同，只是压缩方式不同：**7z 包小约三分之一**，Windows 11 22H2 以后资源管理器可直接解压；Windows 10 或习惯 ZIP 的用户下载 ZIP 包即可。

ZIP 内附发布者的数据快照（含初始源配置与设置）。升级时用新版文件夹里的程序文件覆盖旧文件夹（`LibreTV-Data` 除外），即可保留你自己的数据。

> **2.19.2-desktop.6 起改为目录分发**：早先的单文件 EXE 每次启动都要把约 460 MB 运行时重新解压到临时目录并被杀毒软件逐个扫描，窗口要等近 30 秒；改为目录分发后启动约 **1 秒**。功能完全一致。

> **请认准最新版**：Android **2.0.14**、Windows **2.19.2-desktop.8**。更早的 Android 版本（2.0.10）存在片源搜索失效的问题，不建议使用；其余旧版功能可用，但少了清单多镜像与老系统 TLS 1.2 支持。

### Android 通用版

<div align="center">

**[⬇ 下载 Android APK](https://github.com/wangshaojie/LibreTV-App/releases/download/android-v2.0.14/LibreTV-App-Android-v2.0.14-universal-android5.apk)** &nbsp;·&nbsp; [查看所有版本](https://github.com/wangshaojie/LibreTV-App/releases)

</div>

版本 `2.0.14` · 最低 Android 5.0 · 手机与 Android TV 通用 · 只有一个安装包

> 手机直接下载安装；电视可用 U 盘或电视文件管理器打开 APK。两端均为**同一安装包覆盖升级**，保留源配置、收藏和观看记录，无需先卸载。

## 界面预览

### Android TV

**首页：电影 / 剧集海报、最近观看与遥控器导航**

![Android TV 首页](docs/screenshots/android-tv-home.png)

**直播频道：分组、搜索、批量测活与可连接筛选**

![Android TV 直播频道与测活结果](docs/screenshots/android-tv-channels.png)

**直播播放页：全屏沉浸式，观看时不显示任何按钮或标题**

![Android TV 直播播放](docs/screenshots/android-tv-live.png)

**播放页控制面板：按上下键呼出，几秒后自动淡出**

![Android TV 播放控制面板](docs/screenshots/android-tv-player-controls.png)

### Windows

首页与聚合搜索 · 直播频道分组 · 源管理 · 批量测活（可一次检测所有源中可用的频道）

<img width="1264" height="831" alt="Windows 首页" src="https://github.com/user-attachments/assets/27d2f58d-cdd1-4c25-897d-2965d6466cf1" />
<img width="1264" height="829" alt="Windows 直播频道" src="https://github.com/user-attachments/assets/a0eeed2d-d53a-4c7c-bfd0-6e60cd978f45" />
<img width="1264" height="775" alt="Windows 聚合搜索" src="https://github.com/user-attachments/assets/98da3a7c-5e43-4582-a68f-3e5ad38933fd" />
<img width="1264" height="775" alt="Windows 批量测活" src="https://github.com/user-attachments/assets/93cc0020-1ebb-46fe-b3c6-bda0a23ca18a" />

## 快速上手

**Windows**

1. 下载 ZIP 包，**完整解压**到有写入权限的文件夹。
2. 打开解压出来的 `LibreTV-App` 文件夹，双击 `静观TV.exe`（约 1 秒启动）。
3. 在「设置 → 源管理」中管理自己的片源：M3U 播放列表加到**直播源**，Apple CMS JSON API 加到**点播源**。
4. 首页用于影片搜索，顶部「直播」用于浏览 M3U 频道和分组。

**Android**

1. 下载 APK 并安装（电视需允许「安装未知来源应用」）。
2. 首页点海报即可查找片源；「点播」页全源搜索同名影片。
3. 「源管理」管理片源，「直播」浏览频道，遥控器方向键移动、确定键选择。

## 常见问题

**Q：需要账号或服务器吗？**
不需要。Windows 版内置本地服务，Android 版完全在设备上运行。数据都存在你自己的设备里。

**Q：为什么我的老电视连不上某些源？**
部分片源站点已不可用，或需要代理——这是站点和网络的状况，不是应用的问题。片源之间互不影响：某个源测不通就只是这个源不可用，其他源照常搜索。用「源管理 → 全部测活」检测一遍，把连不上的关掉即可。2.0.13 起修复了老系统 IPv6 路由缺失导致连不上站点的问题（自建 IPv4 通道并保留正确的 SNI 与证书校验），2.0.14 起官方片源清单改为多地址镜像，主地址拉不到会自动换下一个，电视这类没有代理的设备也能持续收到清单更新。

**Q：播放的时候怎么有广告？**
那是**片源站自己插的**，不是本应用加的——本应用既不投放广告，也不从中获利，更不参与片源站的任何分成。不同片源的干净程度不一样，介意的话在「源管理」里换一个源，或用测活把带广告、不可用的源关掉。

**Q：可以加自己的源吗？**
可以。M3U 播放列表填「直播源」，Apple CMS JSON API 填「点播源」，两个端都支持。

**Q：Windows 版怎么升级？**
关闭旧版，用新版 `LibreTV-App` 文件夹里的程序文件覆盖旧文件夹中除 `LibreTV-Data` 以外的全部内容。不要用发布包里的示例数据覆盖你已经使用的数据目录。

更多细节见 [Android 说明](android/README.md) · [Windows 说明](desktop/README.md)

## 参与共建

这是我们最想看到的一件事 ⭐

- **点个 Star** —— 让更多人知道这个项目，也让更多电视机能用上。
- **报问题** —— 把你遇到的老电视型号、报错和片源失效情况告诉我们，这类问题只有真机能复现。
- **提 PR** —— 欢迎提交修复与改进，尤其欢迎补充片源解析、播放器体验和电视端适配。

## 从源码构建

**Windows 版**（需要 Node.js 22 或更新的受支持版本）：

```powershell
npm ci
cd desktop
npm ci
npm run dist
```

**Android 版**（需要 JDK 17、Gradle 8.9、Android SDK 35）：

```sh
gradle -p android testDebugUnitTest assembleDebug
```

发布用签名 APK 由 GitHub Actions 构建，签名密钥不进入仓库也不进入下载附件。更多开发说明见 [desktop/README.md](desktop/README.md) 与 [android/README.md](android/README.md)。

## 上游与许可

本项目是**非官方衍生项目**，上游项目及原作者的贡献和署名予以保留。

- 上游项目：[LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV) · 版本 `2.19.2` · 基础提交 `dd1c744c40d60ee553bd9f7eb3df55dd79a8f7dc`
- 原项目说明：[README.upstream.md](README.upstream.md)
- 本仓库新增：Windows 便携封装、系统代理网络适配、Android 手机 / TV 原生客户端、官方片源清单远程更新、批量测活、构建 / 验证脚本和发布文档

LibreTV 和本项目按 **AGPL-3.0-or-later** 提供，完整条款见 [LICENSE](LICENSE)。Electron、Chromium 及其他第三方组件遵循各自许可证。

> 本项目提供客户端和播放功能，**不制作或托管影视内容**。请仅使用有权访问的数据源和内容。

## 项目性质

静观 TV 目前**完全出于个人爱好**而做：免费、开源、不投放广告、不做收费或任何商业化，也不收集任何用户数据。

需要说明的是，部分片源在播放过程中会插入广告——**那是源站自身的行为，与本项目无关**；本项目不对片源站的内容和广告负责，也无法替你过滤它们。遇到带广告的源，直接换一个或关掉即可。
