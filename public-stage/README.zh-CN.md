# TVHome：MiTV-MFTR0 桌面候选版

TVHome 是一个独立的 Java Android TV 桌面候选版。它保持电视原厂桌面启用，只在安装和测试时使用标准 Android Intent 与电脑端 ADB；不需要 Root、系统签名、无障碍接管或设备所有者权限。它不是小米官方产品。

## 功能

- 适合遥控器的四列应用网格和常用应用 Dock。
- 对已记录的 MiTV-MFTR0 固件使用明确的信号源路径，设备不匹配时保留安全回退。
- 电视设置入口和普通应用启动。
- 电视端系统图片选择器导入背景图，也提供电脑端 ADB 单图导入回退。
- 背景压暗 0–70%，每 10% 一档，默认 30%。
- 安装前保存原 HOME，安装/恢复脚本逐项检查并读回结果。

原生信号源预览面板仍由电视原厂软件负责；静态截图不能证明 HDMI 动态画面已经通过。具体候选版证据和需要人工确认的项目见 Release 说明。

## 构建

固定工具链为 JDK 17、AGP 8.5.2、Gradle 8.9、Android SDK Platform 34、Build Tools 34.0.0、minSdk 26。在 `launcher` 目录运行：

```text
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

Release 输出故意保持未签名。请在自己的电脑上使用私有 release key 签名，不要提交 keystore 或密码。

## 安装

使用 Release 中的 InstallKit，并显式填写 ADB serial：

```text
Install-TVHome.ps1 -Serial <TV_IP:PORT> -ApkPath .\TVHome-MFTR0-1.0.0-rc.1.apk
```

默认只安装并启动 TVHome。直接启动正常后，才按需加 `-ActivateHome`。脚本不会卸载应用、清除应用数据、停用原厂桌面，也不会修改画面、声音、网络、CEC 或输入法设置。

## 兼容性

当前精确 profile 面向型号 `MiTV-MFTR0` 及 profile 中记录的公开固件指纹。其它设备不会套用这些厂商专用路由，而会保留普通 Android 回退路径。

这是公开 CleanYourTV 项目的独立衍生项目，不代表小米或上游项目。归属和许可证见 `UPSTREAM.md`、`LICENSE`。
