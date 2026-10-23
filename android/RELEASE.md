# Android 1.0.2 · Android 5 电视 HTTPS 兼容修复

修复旧电视读取 HTTPS 订阅时出现“Trust anchor for certification path not found”的问题。Android 5–7 会补充来自 Mozilla / certifi 的可信根证书，订阅及播放器均适用，仍保留证书链和主机名校验；不会修改电视的系统证书或代理设置。

内置 7 个点播源、9 个直播源。手机与 Android TV 通用，最低 Android 5.0，直接覆盖安装即可，保留源配置、收藏和观看记录。

提供签名 APK、对应源码 ZIP、使用说明和 SHA-256 校验文件。基于 LibreSpark/LibreTV 的非官方客户端，采用 AGPL-3.0-or-later；CA 数据采用 MPL-2.0，第三方许可随包提供。
