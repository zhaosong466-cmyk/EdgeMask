# EdgeMask · 对称黑边 1.3

Android 屏幕遮罩工具：左右对称黑条、手动调节宽度和内边框圆角；兼容设备可选 Root 收窄逻辑显示尺寸。

**AI 编写声明：源码、说明和 APK 由 ChatGPT / Codex 根据用户需求生成。Root 是实验功能，未完成系统性真机兼容测试。**

## 1.3：修正对实际显示的误判

小米 13 Ultra、Android 16 的用户反馈：1.2 显示“已校验居中”，截图比例正常，但手机实际画面仍被横向拉伸。1.2 检查的是 WindowManager 状态和输入服务报告的映射，**这些数值不能证明最终面板输出没有缩放**。此前停用侧边遮罩的判断不足。

- 暂时限制小米 13 Ultra（设备代号 `ishtar` / 对应型号）在 Android 16 及以上使用 Root 收窄，避免重复已反馈的拉伸。普通黑边和圆角继续可用。这是根据用户反馈采取的保守限制，不代表每个系统版本都已测试。
- Root UI 不再声称“已验证实际居中”。其他设备在预览期间仍绘制侧边遮罩，用户直接检查手机上的圆形、正方形和触摸后才能确认。
- 增加“画面被拉伸：恢复并保留黑边”。反馈会恢复原始尺寸与缩放，并限制当前系统构建继续使用 Root 收窄。
- 增加“复制显示诊断（只读，需要 Root）”：读取机型、系统构建、逻辑/物理尺寸、DPI、缩放、输入映射和可用的显示几何参数。诊断不修改设置，不截图，不导出窗口/应用层名称，也不会自动上传。
- 仍保留独立回退进程、15 秒预览、原始尺寸与缩放备份、手动恢复及重启/更新恢复。

**此版尚未实现该小米 ROM 的无拉伸 Root 收窄。需要具体系统构建与显示诊断后继续适配；不会通过猜测驱动设置或修改系统分区来宣称修复。**

## 下载与使用

先在旧版点击“恢复原始显示并关闭遮罩”，再覆盖安装 [EdgeMask-1.3.apk](apk/EdgeMask-1.3.apk)。Android 8.0 及以上，与 1.0–1.2 使用相同签名。

1. 授予本应用无障碍服务，或“在其他应用上层显示”权限。
2. 选择显示方式，调节每侧黑条宽度和圆角，点击“启用 / 应用当前设置”。
3. 小米 13 Ultra / Android 16：使用普通模式；若要排查 Root 输出问题，点击复制显示诊断，按需手动提供结果。
4. 其他机型可申请 Root，再开启 Root 同步。首次建议每侧 1%。每次变更后直接看手机，确认比例和触摸正常；15 秒内未确认会恢复。
5. 使用结束时点击底部恢复按钮，确认恢复成功后再卸载或撤销 Root。

## 功能与权限

- 普通黑条每侧 0–25%，左右同步；内边框圆角 0–160 dp。
- 滑块实时调整，±1 微调；Root 宽度更新合并为约 450 ms 一次。
- 普通模式遮挡内容，不重排应用、不修改系统显示尺寸。
- Root 模式使用 `wm size` 与 `wm scaling off`，DPI 不变；圆角仍由遮罩绘制。系统报告检查和人工确认是不同步骤。
- Root 每侧最多 15%，可见短边至少 320 dp。上限依据原始尺寸、DPI、方向计算；例如 1440×3200、DPI 560 的竖屏为每侧 11%。
- 只支持主用户、主显示器和当前逻辑尺寸等于物理尺寸的 Root 基线；已有较低分辨率时使用普通遮罩。
- 原生 Java，无第三方运行时依赖，无网络权限；默认关闭 Root。

| 显示方式 | 权限 | 行为 |
| --- | --- | --- |
| 无障碍遮罩 | 本应用无障碍服务 | 触摸穿透；不读取窗口文字、不订阅事件、不执行手势 |
| 普通悬浮窗 | 在其他应用上层显示 | 黑条和圆角方块内拦截触摸；中央正常操作 |
| Root 同步 | Root + 任一种遮罩权限 | 收窄逻辑尺寸；需要兼容检查及人工观察，部分设备被限制 |
| 只读诊断 | Root | 读取显示参数，用户选择是否复制；不启用 Root 收窄 |

## Root 回退与实际限制

修改前备份原始逻辑尺寸、尺寸覆盖状态和缩放模式（`auto` / `off`）。缩放状态无法读取时拒绝修改。原来没有覆盖时使用 `wm size reset`；已有覆盖则恢复原来的 `WxH`。缩放按备份恢复，不能一律改为 `auto`。1.1 旧备份只恢复尺寸，因为旧版未修改缩放。

独立回退进程先启动，再串行执行会话中的尺寸、缩放变更和恢复。尺寸恢复失败时仍尝试恢复缩放。每次变更必须确认；拖动不无限延长同一次 15 秒预览。确认后前台服务续约，进程失联时在正常调度下约 20 秒内尝试恢复；约每 5 秒复查系统报告的映射，不能据此保证厂商最终输出状态。

手动关闭、任务移除、遮罩权限失效、旋转或监控异常时请求恢复。恢复后重读尺寸和缩放，失败则保留备份、限制新 Root 会话。备份使用设备保护存储；启动、开机和更新时尝试恢复，**不会开机重新应用 Root 修改**。

这些保护不能保证所有 ROM 无黑屏或触摸错误。回退依赖系统命令、Root 授权和进程调度；命令卡死时必须等当前命令返回后串行恢复。卸载、清除数据、撤销 Root、ROM 杀进程或系统服务卡死可能阻止恢复。截图正常也不能证明手机实际比例正常。

不修改 DPI、面板驱动、刷新率、系统分区、厂商属性或开机脚本。圆角是视觉遮罩，不改变系统窗口形状。Root 旋转时恢复并结束会话，普通遮罩支持横竖屏。

## 备用恢复

优先使用底部按钮或 Root 通知中的恢复操作。应用内“查看备用恢复方法”根据当前备份生成命令。对于原先使用默认尺寸、自动缩放的手机，可从此前已授权的 ADB 执行：

```bash
adb shell su -c 'wm size reset; wm scaling auto'
```

原先已有修改时使用原来的 `WxH` 和缩放模式。例如原始设置为 1080×2400、缩放关闭：

```bash
adb shell su -c 'wm size 1080x2400; wm scaling off'
```

DPI 未修改，无需重置密度。恢复确认前不要卸载、清除数据或撤销 Root。

## 验证与构建

通过 SDK 35 编译、APK 签名检查、60 项参数/映射/兼容限制/诊断过滤检查，以及 24 个独立修改与回退进程的模拟黑盒场景。模拟测试不能代替真机验证。1.2 的上述用户反馈证明，软件映射检查不能验证最终面板比例。

Android Studio 或 JDK 17、Gradle 8.9、Android SDK Platform 35：

```bash
gradle :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。自行构建的签名可能不同；发行签名密钥不在仓库。

纯 Java 与模拟回退测试：

```bash
mkdir -p build/safety
javac -d build/safety app/src/main/java/com/edges/mask/DisplayProfile.java app/src/main/java/com/edges/mask/DisplayScaling.java app/src/main/java/com/edges/mask/DisplayProjection.java app/src/main/java/com/edges/mask/RootCompatibility.java app/src/main/java/com/edges/mask/DiagnosticGeometry.java app/src/main/java/com/edges/mask/RootShell.java app/src/main/java/com/edges/mask/GuardScript.java tests/SafetyChecks.java
java -cp build/safety com.edges.mask.SafetyChecks
python3 tests/watchdog_checks.py build/safety
```

实现依据：[AOSP WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/WindowManagerShellCommand.java)、[LogicalDisplay](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/LogicalDisplay.java)、[DisplayViewport](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/hardware/display/DisplayViewport.java)。设备代号依据 [MiCode 内核开源列表](https://github.com/MiCode/Xiaomi_Kernel_OpenSource)。这些开源实现不能替代厂商 ROM 的实际输出验证。
