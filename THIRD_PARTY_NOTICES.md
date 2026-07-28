# 第三方组件与许可

`mediaviewer` 使用以下主要开源组件。各组件版权归原作者所有；本文件不替代
组件仓库中的完整许可文本。

| 组件 | 固定版本 | 许可 |
| --- | --- | --- |
| AndroidX Core、Activity、Lifecycle、Navigation、Room、DataStore、Compose、Test | 见 `gradle/libs.versions.toml` | Apache License 2.0 |
| Kotlin、Kotlin Coroutines、Kotlin Serialization、KSP | 见 `gradle/libs.versions.toml` | Apache License 2.0 |
| OkHttp、Okio、MockWebServer | 5.3.0 | Apache License 2.0 |
| Coil | 3.5.0 | Apache License 2.0 |
| LibVLC Android | 4.0.0-eap29 | GNU LGPL 2.1 或更高版本 |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| Robolectric | 4.16.1 | MIT License |

项目与许可原文：

- AndroidX：https://source.android.com/docs/setup/about/licenses
- Kotlin：https://github.com/JetBrains/kotlin
- OkHttp：https://github.com/square/okhttp
- Coil：https://github.com/coil-kt/coil
- LibVLC：https://code.videolan.org/videolan/vlc-android
- GNU LGPL 2.1：https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- JUnit 4：https://github.com/junit-team/junit4
- Robolectric：https://github.com/robolectric/robolectric

Debug APK 动态/静态包含方式以 Gradle 最终依赖报告为准。发布或重新分发 APK 前，
应同时保留本说明，并遵守 LibVLC/VLC 对应版本附带的完整 LGPL 通知与源码获取要求。
