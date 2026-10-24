# LibreTV-App Android

原生 Android 客户端，**兼容版和新版均兼容手机和 Android TV**。应用包名 `com.wangshaojie.libretv`，版本 `2.0.3`。Legacy 兼容版最低 Android 5.0 / API 21，推荐 5–7.1；Modern 新版最低 Android 8.0 / API 26。两版功能一致，使用相同包名及签名，可按设备覆盖升级，不能并存。

Legacy 兼容版为 Android 5–7 补充 Mozilla / certifi 根证书，以兼容旧电视缺失新 CA 导致的 HTTPS 订阅读取失败。应用仍验证证书链和主机名，不修改系统证书。根证书来源、固定提交与许可见 APK 的 assets/ca-bundle-source.txt。

2026-10-23 更新内置源至 7 个点播源、9 个直播源。1.0.0 用户覆盖安装后会自动补入新增 6 个源，按类型与地址去重，仅执行一次，不覆盖个人源、收藏和历史。

## 下载与实机截图

Android 2.0.3 已发布：

- [Android 5.0+ 兼容版 APK](https://github.com/wangshaojie/LibreTV-App/releases/download/android-v2.0.3/LibreTV-App-Android-v2.0.3-legacy-android5.apk)
- [Android 8.0+ 新版 APK](https://github.com/wangshaojie/LibreTV-App/releases/download/android-v2.0.3/LibreTV-App-Android-v2.0.3-modern-android8.apk)
- [发布说明、源码和校验文件](https://github.com/wangshaojie/LibreTV-App/releases/tag/android-v2.0.3)

2.0.3 解码失败时给出可读提示，并修复部分 Android 版本上回车键无法打开海报详情。覆盖安装保留数据。

2026-10-24 实机截图（Android 5.0.1 电视，2.0.3 兼容版）：

![电视首页](../docs/screenshots/android-tv-home.png)

![直播频道与批量测活](../docs/screenshots/android-tv-channels.png)

![直播播放页](../docs/screenshots/android-tv-live.png)

## 常见问题：只有声音没有画面

部分早期电视（Android 5–7）只有硬件视频解码器，没有软件解码器兜底。以下情况会出现「有声音、画面全黑」：

- 电视的硬件解码器进入异常状态（多见于连续切台、换线路之后）。
- 码流编码超出该芯片的解码能力，例如清晰度过高或编码方式不常见。

处理顺序：

1. 在播放页按「重试」，应用会重建解码器再试一次。
2. 返回详情点「换线路」换一条线路，或在直播列表选择更低清晰度的频道（例如标有 576p / SD 的同名台）。
3. 若电视上所有视频都只有声音没有画面，请重启电视：断电 10 秒再开机（或在系统设置里重启），电视的视频通路会重新初始化。

2.0.3 起，解码失败时应用会直接说明原因并给出换线路、降低清晰度或重启电视的建议。少数早期电视的硬解在反复切换后仍可能进入异常状态，此时重启电视即可恢复。
## 2.0 界面与功能

新增海报推荐首页、电影 / 剧集分类、最近观看、聚合搜索卡片、原生详情选集、源启用开关，以及源与频道批量测活。手机采用底部导航，Android TV 采用侧栏与遥控器焦点。

- 「首页」浏览推荐；「点播」全源搜索；「直播」选订阅与分组；「我的」管理收藏、记录及备份。
- 「源管理 → 全部测活」检测片源；「直播 → 当前列表测活 / 全部测活」检测频道。可停止、筛选异常及重测，结果回写本机。
- 首页推荐来源可切换为豆瓣或已启用 CMS 源，推荐失败时使用缓存或片源数据。

[完整功能表](FEATURES.md) · [隐私说明](PRIVACY.md)

## 功能与操作

- M3U/M3U8 订阅导入、节目搜索和分组筛选。
- Apple CMS JSON 多源搜索、详情、线路和选集。
- Media3 / ExoPlayer 原生 HLS、DASH、MP4 等播放，支持直播与自动下一集。
- 收藏、最近观看、点播续播，以及源配置 JSON 导入/导出。
- 手机触屏操作；Android TV 方向键移动焦点、确定键操作、返回键退出、菜单键/长按收藏。
- 同一 APK 不含 CPU 专用原生库，可在常见 ARM32、ARM64、x86、x86_64 设备安装；实际解码能力取决于设备。
- 初始源来自此前 Windows 公开下载包中发布的配置。Android 数据存于应用私有目录，不与 Windows 的浏览器数据库直接共享。

这是独立原生实现，沿用 LibreTV 的源协议和使用方式。EPG、Spider/网页解析、离线下载、Windows 版的全部高级功能尚未移植。程序不提供影视内容，不保证第三方线路可用。

## 安装

从仓库 Releases 按设备下载 `LibreTV-App-Android-v2.0.3-legacy-android5.apk` 或 `LibreTV-App-Android-v2.0.3-modern-android8.apk`，允许你选择的安装工具安装应用后打开。无需 Windows 程序、Docker、Node.js 或自行搭建服务器。不会请求通讯录、摄像头、定位或全盘存储权限。

手机桌面和电视桌面均有启动入口。手机播放页可切换横/竖屏，电视采用横屏和明显的焦点边框。

直播源填写 M3U 列表地址，点播源填写 Apple CMS JSON API。HTTP IPTV 流可以播放；HTTPS 仍正常验证证书。网络请求遵循 Android 的网络环境/系统代理配置，具体连通性取决于当前设备。

## 构建与签名

使用 JDK 17、Gradle 8.9、Android SDK 35：

```sh
gradle -p android testLegacyDebugUnitTest testModernDebugUnitTest assembleLegacyDebug assembleModernDebug
```

正式包通过 `.github/workflows/android-release.yml` 构建。仓库 Secrets 中保存 `ANDROID_KEYSTORE_BASE64` 和 `ANDROID_KEYSTORE_PASSWORD`，签名别名 `libretv-app`。签名密钥不会提交到 Git 或放入下载附件；必须保留相同密钥才能覆盖安装后续版本。

工作流运行单元测试、Lint，以及 Android 5 手机、Android 15 手机和两个 Android TV 组合的设备测试，覆盖首页、搜索、HTTP 播放、选集收藏、批量测活、取消和数据持久化，并导出截图用于布局检查。通过后发布签名 APK。界面测试使用本地合成片目和视频，测试数据不会进入正式安装包。

## 许可与来源

基于 LibreSpark/LibreTV 的非官方客户端。Android 原生实现修改日期：2026-10-23。项目继续使用 AGPL-3.0-or-later；原许可证见根目录 LICENSE，应用内也包含完整许可证。Media3/ExoPlayer 遵循 Apache-2.0，其他组件遵循各自许可证。

点播首页的 Spider/网页源文字原为固定能力说明，2.0.3 已改成正常搜索引导；无需据此更换内置点播源。详情使用与 Windows 端一致的 videolist API。
