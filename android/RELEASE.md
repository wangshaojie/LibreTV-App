# Android 1.0.2 · Android 5 电视 HTTPS 兼容修复

修复旧电视读取 HTTPS 订阅时出现“Trust anchor for certification path not found”的问题。Android 5–7 会补充来自 Mozilla / certifi 的可信根证书，订阅及播放器均适用，仍保留证书链和主机名校验；不会修改电视的系统证书或代理设置。

内置 7 个点播源、9 个直播源。手机与 Android TV 通用，最低 Android 5.0，直接覆盖安装即可，保留源配置、收藏和观看记录。

已在 Android 5.0.1 实体电视验证：原来证书失败的默认 HTTPS 订阅可正常读取频道列表。另有手机、Android TV 模拟器回归测试，以及未知自签名证书拒绝测试。

提供签名 APK、对应源码 ZIP、使用说明和 SHA-256 校验文件。基于 LibreSpark/LibreTV 的非官方客户端，采用 AGPL-3.0-or-later；CA 数据采用 MPL-2.0，第三方许可随包提供。
