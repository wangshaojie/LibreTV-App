# LibreTV-App Windows 免安装版 v2.19.2-desktop.2

2026-10-23 更新下载包中的源配置：现含 **7 个点播源、9 个直播源**。本次为数据更新，EXE 仍为 2.19.2-desktop.2。Android 手机 / TV 通用版另行发布，当前为 2.0.6。

## 下载

- `LibreTV-App-v2.19.2-desktop.2-Windows-x64.zip`：Windows 10/11 x64 免安装包。
- `LibreTV-App-v2.19.2-desktop.2-Source.zip`：本版本对应的完整应用源码与构建脚本。
- `SHA256SUMS.txt`：下载文件的 SHA-256 校验值。

Windows ZIP 内包含 `LibreTV.exe`、发布者的 `LibreTV-Data` 数据快照、中文使用说明、更新日志和许可证。数据快照保留源配置、设置与观看记录；浏览器缓存、Cookies、运行日志和锁文件未包含。

## 使用

完整解压 ZIP 后双击 `LibreTV.exe`。无需安装 Node.js、Docker 或其他运行环境。

升级用户请先关闭旧版，仅替换 EXE，保留自己的 `LibreTV-Data`，不要用随包数据覆盖已有记录。希望从空白配置开始的用户，可在首次运行前将随包数据目录移走备份。

已有 Windows 用户若只想补充最新源，请在源管理的订阅入口添加：
https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/sources/latest.json

ZIP 内另附 `LibreTV-Sources.json`，供迁移、托管订阅或导入 Android 使用。

## 本版本

- 基于 LibreTV 2.19.2 的非官方 Windows 桌面封装。
- 修复内置服务没有跟随 Windows 系统代理/PAC、导致部分 M3U 订阅下载失败的问题。
- 保留逐跳目标校验，兼容重定向、Range、流式传输和超时中断。
- 本地数据随 EXE 保存，便于备份和迁移。
- Android 通用版当前为 2.0.6，兼容手机及 Android TV：https://github.com/wangshaojie/LibreTV-App/releases/tag/android-v2.0.6

在线内容需要联网，线路是否可播取决于网络、地区、源站和编码。本构建未做数字签名。

## 上游与许可

上游：[LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV)。应用及桌面封装采用 AGPL-3.0-or-later，保留原作者署名，具体条款见源码和安装包中的 LICENSE。
