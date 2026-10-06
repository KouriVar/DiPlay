# 深蓝车机首页

首页依据 Figma `W7G3YzF1rw7wjW0AbEOYct` 的 `1:2` 画板实现，设计尺寸为 1920 × 1080。

- 横向排列车机主页、选择设备、连接手机和软件设置，底部保留进入 CarPlay 按钮。
- 选择设备显示实际保存的手机名称；就绪、连接中、已连接和初始化错误来自现有连接状态。
- 首页不再提供 USB 连接入口。连接、配对、设置及 CarPlay 投屏继续使用原有实现。
- 本地视频作为静音循环背景，转换为 1080p H.264；离开首页即释放播放器，重新进入时恢复播放。解码失败时保留静态首帧。
- 五个图标使用 Figma 导出的原始 SVG，字体为用户提供的 MiSans。所有展示资源均打包在应用内。
- 玻璃卡片共享一张低分辨率模糊背景，每秒更新约六次；视频保持硬件解码。短窗口、分屏及窄窗口保留紧凑布局。

## 本地测试包

遵循 [BUILD.md](BUILD.md) 配置 Android SDK 和显式的本地认证资产，然后运行：

```sh
./gradlew :common:testDebugUnitTest --tests '*HomeCompactLayoutTest' --tests '*DeepalHomeViewTest'
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug :mobile:lintDebug
```

测试包显示为 **Deepal CarPlay 测试**，包名 `com.shihab.diplay.hudtest`，使用本机 Android debug keystore 签名，可与原版并存。以后更新测试包须继续使用同一签名文件。首次使用需要在测试包内选择手机并配置连接方式。

认证文件、签名文件、构建输出和参考截图只保存在本机，不提交到 Git。

上车测试时退出另一款投屏应用，检查手机选择、无线连接、全屏显示及往返设置页；返回 CarPlay 后确认音乐和触控正常。
