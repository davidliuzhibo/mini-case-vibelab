# 微缩奇案 · MiniCase

PICO Spatial SDK MR 游戏，版本 0.4 / versionCode 4。

三个微缩现场、问话、物证检查、线索关联和指认。捏合拖动抽屉、门盖与物件，点击人偶问话；案情板与卷宗互斥显示。案件及人物均为虚构。

识别到合适真实桌面时使用桌面摆放，未识别到时使用虚拟台面。游戏逻辑与素材均在本地，不需要业务服务器。以系统手势和眼手/手势射线交互，不依赖手柄。

## 构建

Gradle 工程根目录即本仓库根。需要 JDK 21 和 Android SDK 35。模型、纹理、音频、图标均随源码提供；构建不需要调用生成模型。

```sh
./gradlew testDebugUnitTest assembleRelease --no-daemon --max-workers=1
```

Windows 使用 gradlew.bat。通过 ANDROID_HOME 指定 SDK；不包含本机配置、访问令牌或签名私钥。产物 app/build/outputs/apk/release/app-release-unsigned.apk 供平台签名。

代码许可见 LICENSE，素材来源见 THIRD_PARTY_NOTICES.md。AI 编程工具和模型按作者确认：Claude Code + Opus 5.5。
