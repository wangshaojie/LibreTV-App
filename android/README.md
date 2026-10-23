# LibreTV-App Android

原生 Android 客户端，**同一 APK 兼容手机和 Android TV**。应用包名 `com.wangshaojie.libretv`，版本 `1.0.1`，最低 Android 5.0 / API 21。

2026-10-23 更新内置源至 7 个点播源、9 个直播源。1.0.0 用户覆盖安装后会自动补入新增 6 个源，按类型与地址去重，仅执行一次，不覆盖个人源、收藏和历史。

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

从仓库 Releases 下载 `LibreTV-App-Android-v1.0.1-universal.apk`，允许你选择的安装工具安装应用后打开。无需 Windows 程序、Docker、Node.js 或自行搭建服务器。不会请求通讯录、摄像头、定位或全盘存储权限。

手机桌面和电视桌面均有启动入口。手机播放页可切换横/竖屏，电视采用横屏和明显的焦点边框。

直播源填写 M3U 列表地址，点播源填写 Apple CMS JSON API。HTTP IPTV 流可以播放；HTTPS 仍正常验证证书。网络请求遵循 Android 的网络环境/系统代理配置，具体连通性取决于当前设备。

## 构建与签名

使用 JDK 17、Gradle 8.9、Android SDK 35：

```sh
gradle -p android testDebugUnitTest assembleDebug
```

正式包通过 `.github/workflows/android-release.yml` 构建。仓库 Secrets 中保存 `ANDROID_KEYSTORE_BASE64` 和 `ANDROID_KEYSTORE_PASSWORD`，签名别名 `libretv-app`。签名密钥不会提交到 Git 或放入下载附件；必须保留相同密钥才能覆盖安装后续版本。

工作流先运行单元测试和 Lint，再在手机、Android TV 模拟器运行启动、导航、数据持久化和原生 H.264 播放测试，全部通过后才允许发布 APK。

## 许可与来源

基于 LibreSpark/LibreTV 的非官方客户端。Android 原生实现修改日期：2026-10-23。项目继续使用 AGPL-3.0-or-later；原许可证见根目录 LICENSE，应用内也包含完整许可证。Media3/ExoPlayer 遵循 Apache-2.0，其他组件遵循各自许可证。
