# Android 1.1.0 · 兼容版 / 新版

- Legacy 兼容版：最低 Android 5.0，推荐 Android 5–7.1 旧设备。保留旧系统 Mozilla / certifi 根证书兼容处理。
- Modern 新版：最低 Android 8.0，使用系统证书库，不打入旧系统 CA 数据。
- 两版均兼容手机和 Android TV，内置 7 个点播源、9 个直播源，功能一致。
- 修正点播首页将 Spider/网页源限制作为固定提示、容易被误认为报错的问题，现在显示输入片名的搜索引导。
- 点播详情改为与 Windows 一致的 ac=videolist&ids= 请求，兼容正常的 Apple CMS 源。没有新增 Spider 引擎或网页解析能力。

根据 Android 版本下载一份 APK，直接覆盖安装 1.0.x 即可。两版使用相同包名和原签名，保留个人配置、收藏和观看记录；同一设备不能将两版作为独立应用并存，不要先卸载旧版。

提供两份签名 APK、完整对应源码 ZIP、中文使用说明与校验文件。采用 AGPL-3.0-or-later，第三方许可随包提供。
