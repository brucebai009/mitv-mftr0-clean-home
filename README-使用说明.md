# TVHome 使用说明

## 第一次安装

1. 在电脑安装官方 Android platform-tools，确认 `adb` 可执行。
2. 电视打开开发者选项和 ADB，使用 `Install-TVHome.ps1 -Serial <TV_IP:PORT>`。
3. 脚本会先记录原 HOME、设备型号和固件指纹，再安装并直接启动 TVHome。
4. 直接启动确认正常后，才使用 `-ActivateHome` 让系统 HOME 指向 TVHome。原厂桌面保持启用。

## 背景图

进入“桌面设置 → 背景图 → 选择图片”。支持 JPEG、PNG 和静态 WebP；图片会复制到 TVHome 自己的目录，不会删除或修改原图。导入后可在“已导入图片”列表中切换，也可以选择“无背景图”或“恢复默认背景”。“背景压暗”按 10% 调整，范围为 0–70%。

如果电视没有可用的系统图片选择器，在电脑运行：

```text
Import-Wallpaper.ps1 -Serial <TV_IP:PORT> -ImagePath 'D:\Pictures\wallpaper.jpg'
```

随后仍在电视的背景图列表中选择它。工具只需要 ADB，不需要 Android SDK、JDK 或重新编译。

## 信号源与预览

在桌面打开“信号源”，按设备 profile 提供的 HDMI/ATV/DTMB 条目进入原厂路径。原生预览面板和动态 HDMI 画面由电视原厂组件负责；请以真实遥控器和实时画面验收，不要用静态截图代替。

## 恢复原厂 HOME

安装脚本生成的快照必须保留在本机。恢复时运行：

```text
Restore-OriginalHome.ps1 -Serial <TV_IP:PORT> -SnapshotPath .\tvhome-state\tvhome-snapshot-YYYYMMDD-HHMMSS.json
```

脚本会核对设备身份、当前用户、原 HOME 包是否存在，逐条检查命令，并读回 HOME resolver 与实际前台。它不会卸载 TVHome、清除 TVHome 数据或清理其它应用。

## 验收清单

- 真实遥控器 Home 是否进入 TVHome。
- 左右/上下移动和快速反向时焦点是否跟手。
- 原有浏览器、播放器及登录/历史数据是否保留。
- 导入背景图、切换、返回桌面和重启后是否仍保留。
- 点击信号源是否进入正确 HDMI，已连接设备的画面和声音是否正常。
- 原生预览是否随真实 HDMI 画面变化，退出后全屏是否正常。
- 原厂画质、声音、输入法和网络设置是否仍可使用。
- 恢复脚本是否能回到原厂 HOME。
