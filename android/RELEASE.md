# Android 2.0.2 · 修复电视遥控器无法打开海报结果

在真实 Android 5 电视上复现：从首页进入搜索后，结果海报高亮，但遥控器确认键和回车键无法打开详情。此前卡片子视图自己处理触屏点击，网格控件却没有绑定条目激活事件。

- 搜索、分类、收藏与历史共用的海报网格统一由 GridView 处理点击、长按和遥控器激活。
- 海报子视图不再抢占网格焦点；保留选中高亮和菜单键管理收藏。
- 增加真正发送方向键确认、回车和触屏事件的回归测试，确认进入影片详情。
- 保留 2.0.1 的视频直连优先及换线路入口。

兼容版最低 Android 5.0（推荐 5–7.1），新版最低 Android 8.0；均支持手机和电视。使用原签名与包名，覆盖安装保留配置、收藏和历史。

## 下载

| 安装包 | 适用设备 | 文件 |
| --- | --- | --- |
| 兼容版 Legacy | Android 5.0 及以上，推荐 5–7.1 旧设备 | [LibreTV-App-Android-v2.0.2-legacy-android5.apk](https://github.com/wangshaojie/LibreTV-App/releases/download/android-v2.0.2/LibreTV-App-Android-v2.0.2-legacy-android5.apk) |
| 新版 Modern | Android 8.0 及以上，使用系统 HTTPS 证书库 | [LibreTV-App-Android-v2.0.2-modern-android8.apk](https://github.com/wangshaojie/LibreTV-App/releases/download/android-v2.0.2/LibreTV-App-Android-v2.0.2-modern-android8.apk) |

内置 7 个点播源、9 个直播源。手机直接下载安装；电视可用 U 盘或电视上的文件管理器打开 APK。完整说明见 [仓库首页](https://github.com/wangshaojie/LibreTV-App) 与 [android/README.md](https://github.com/wangshaojie/LibreTV-App/blob/main/android/README.md)。

## 实机截图

以下截图拍摄于 2026-10-24，来自 Android 5.0.1 电视上的 2.0.2 兼容版。海报、频道和视频画面来自使用时的第三方数据源，仅用于展示客户端界面。

**首页：电影 / 剧集海报、最近观看与遥控器导航**

![Android TV 首页](https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/docs/screenshots/android-tv-home.png)

**直播频道：分组、搜索、批量测活与可连接筛选**

![Android TV 直播频道与测活结果](https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/docs/screenshots/android-tv-channels.png)

**直播播放页**

![Android TV 直播播放](https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/docs/screenshots/android-tv-live.png)

首页推荐片单与实际播放源分开：点击推荐后先匹配片源，再进入影片详情选集。第三方片源有效性、播放内容及解码能力以实际播放为准。不提供 Spider 或网页解析引擎。

附签名 APK、对应源码、中文说明与校验文件。基于 LibreSpark/LibreTV 的非官方原生客户端，采用 AGPL-3.0-or-later。
