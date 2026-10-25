# 静观 TV Windows 免安装版 v2.19.2-desktop.4

Windows 免安装版更名为 **静观 TV**，与 Android 手机 / TV 通用版同名。2026-10-24 更新下载包中的源配置：现含 **7 个点播源、9 个直播源**。Android 手机 / TV 通用版当前为 2.0.11。

## 下载

- `LibreTV-App-v2.19.2-desktop.4-Windows-x64.zip`：Windows 10/11 x64 免安装包。
- `LibreTV-App-v2.19.2-desktop.4-Source.zip`：本版本对应的完整应用源码与构建脚本。
- `SHA256SUMS.txt`：下载文件的 SHA-256 校验值。

Windows ZIP 内包含 `静观TV.exe`、发布者的 `LibreTV-Data` 数据快照、中文使用说明、更新日志和许可证。数据快照保留源配置、设置与观看记录；浏览器缓存、Cookies、运行日志和锁文件未包含。

## 使用

完整解压 ZIP 后双击 `静观TV.exe`。无需安装 Node.js、Docker 或其他运行环境。

升级用户请先关闭旧版，仅替换 EXE，保留自己的 `LibreTV-Data`，不要用随包数据覆盖已有记录。希望从空白配置开始的用户，可在首次运行前将随包数据目录移走备份。

已有 Windows 用户若只想补充最新源，请在源管理的订阅入口添加：
https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/sources/latest.json

ZIP 内另附 `LibreTV-Sources.json`，供迁移、托管订阅或导入 Android 使用。

## 本版本

- **新增：启动时自动获取项目维护的官方片源清单**。清单地址由维护者控制，用户只能读取、无法改写。合并规则：新增的源自动补入并默认启用，你手动停用的源保持停用，你自己添加的源不受影响，你删除过的官方源不会被自动加回。清单拉取失败时静默降级，本地配置保持原样，不影响使用。
- 应用更名为 **静观 TV**：窗口标题、任务栏与开始菜单名称、程序菜单、网页界面和安装包内的 EXE 全部同步更新。
- **数据目录名保持 `LibreTV-Data` 不变**：覆盖升级后原有源配置、设置和观看记录继续可用，不需要迁移，也不会丢数据。
- 下载包文件名保持 ASCII（`LibreTV-App-v2.19.2-desktop.4-Windows-x64.zip`），不引入中文 URL 编码问题；包内可直接双击的 EXE 为 `静观TV.exe`。
- 保留逐跳目标校验，兼容重定向、Range、流式传输和超时中断。
- 本地数据随 EXE 保存，便于备份和迁移。
- 上一版 2.19.2-desktop.2 修复了内置服务没有跟随 Windows 系统代理/PAC、导致部分 M3U 订阅下载失败的问题，本次改动不涉及该逻辑。
- Android 通用版当前为 2.0.11（静观 TV），兼容手机及 Android TV：https://github.com/wangshaojie/LibreTV-App/releases/tag/android-v2.0.11

在线内容需要联网，线路是否可播取决于网络、地区、源站和编码。本构建未做数字签名。

## 上游与许可

上游：[LibreSpark/LibreTV](https://github.com/LibreSpark/LibreTV)。应用及桌面封装采用 AGPL-3.0-or-later，保留原作者署名，具体条款见源码和安装包中的 LICENSE。
