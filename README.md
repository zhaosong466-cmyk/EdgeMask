# EdgeMask · 对称黑边 1.2

一个轻量 Android 屏幕遮罩工具：左右对称黑条、可调圆角，以及可选的 **Root 系统显示同步**。

**AI 编写声明：源码、项目说明与 APK 由 ChatGPT / Codex 根据用户需求生成。Root 功能为实验功能，未完成系统性的真机兼容测试；不能保证所有 Android 版本、Root 管理器和厂商 ROM 均稳定。**

## 1.2 修复

- 针对“修改分辨率后被系统拉伸到全屏，黑边消失”：修改逻辑尺寸时同时执行 `wm scaling off`，关闭自动缩放。
- 不再仅凭 `wm size` 的返回值判断成功。修改前先验证主显示器映射可读取；修改后读取当前缩放状态和实际显示/触摸映射，验证逻辑画面以原像素尺寸居中，左右保留预期宽度的黑边。
- 只有上述校验通过才停止绘制额外侧边黑条。命令失败、ROM 忽略缩放设置、映射仍全屏或无法验证时，恢复原始尺寸与缩放，自动退回普通黑边。
- 备份、独立回退进程和备用恢复方法都包含原始缩放设置。确认后每约 5 秒复查映射，检测到再次拉伸会恢复并退回普通黑边。
- 处理 Android 15 及以上的系统栏边距，滚动内容被限制在状态栏与导航栏之间，缩窄时重新读取边距。
- 浅色页面使用深色系统栏图标，避免状态栏白字与页面文字叠在一起。
- 确认按钮和恢复按钮固定在底部，调整参数时无需滚回页面顶部；确认按钮显示剩余秒数，应用尺寸完成并校验后才允许确认。
- Root 滑块的最大值由原始尺寸、DPI 和旋转方向计算。除了每侧 15% 上限，还提前限制到剩余短边至少 320 dp；例如 1440×3200、DPI 560 的竖屏上限为每侧 11%。
- 此次修复依据 1.1 的用户录屏与源代码检查；1.2 尚未在该手机上验证。

## 下载与使用

先确认旧版已恢复原始显示，再安装 [EdgeMask-1.2.apk](apk/EdgeMask-1.2.apk)，Android 8.0 及以上。与此前的 1.0 / 1.1 使用相同签名，可覆盖更新。

1. 授予无障碍服务或“在其他应用上层显示”权限，选择对应显示方式。
2. 普通模式直接点击“启用 / 应用当前设置”，调节宽度和圆角。
3. 如需系统重新排版，点击“检测并申请 Root 权限”，在 Root 管理器中手动允许。
4. 打开“Root 同步系统显示”开关，首次建议每侧 **1%**，再点击启用。
5. 每次显示尺寸改变后，检查画面、键盘及触摸是否正常，在 **15 秒内点击确认并保留**；不确认会自动回退。
6. 停止使用时点击页面底部“恢复原始显示并关闭遮罩”，确认恢复成功后再卸载或撤销 Root。

## 功能

- 普通遮罩每侧宽度 0–25%，左右同步；圆角半径 0–160 dp。
- 滑块实时调整，±1 微调；宽度改变在 Root 模式下合并为约 450 ms 一次，避免每个拖动事件都重配置系统。
- 普通模式只遮住内容，不修改布局；Root 模式修改默认显示器的 **逻辑显示尺寸**，使应用重新排版，DPI 不修改。
- Root 模式关闭自动缩放，并在校验居中显示后由系统显示投影产生两侧留黑；此时只保留圆角遮罩，避免重复遮挡。校验失败则保留普通侧边遮罩并恢复系统设置。
- 默认不使用 Root，没有 Root 也可使用全部原有遮罩功能。
- 原生 Java，无第三方运行时依赖、无网络权限。

| 显示方式 | 权限 | 行为 |
| --- | --- | --- |
| 无障碍遮罩 | 开启本应用无障碍服务 | 触摸穿透；不读取窗口内容、不订阅事件、不执行手势 |
| 普通悬浮窗 | 在其他应用上层显示 | 黑条和圆角方块内拦截触摸；中央正常操作 |
| Root 同步（附加选项） | Root + 上述任一种遮罩权限 | 系统按目标区域重新排版，仍由遮罩绘制圆角 |

## Root 回退保护

- 修改前备份原始逻辑尺寸、尺寸覆盖状态和原始缩放模式（`auto` / `off`）。无法读取缩放状态时拒绝修改。
- 已有尺寸覆盖会恢复为原来的 `WxH`；原先没有尺寸覆盖才执行 `wm size reset`。
- 缩放恢复为原来的 `auto` 或 `off`，不能一律重置为 `auto`。兼容恢复 1.1 的旧尺寸备份；旧版未修改缩放，因此恢复旧备份时不额外修改缩放。
- 先验证独立回退进程已经运行，再执行尺寸变更；回退进程启动失败时不修改显示。
- 会话中的尺寸、缩放修改与恢复由同一个独立进程串行执行，避免延迟的修改命令在恢复之后再次生效。尺寸恢复失败时仍尝试恢复缩放。
- 每次变更必须重新确认。拖动期间不会无限延长同一次 15 秒预览期限。
- 确认后由前台服务续约；应用进程被终止或心跳丢失时，独立回退进程在正常调度下约 20 秒内尝试恢复。
- 手动关闭、移除应用任务、撤销遮罩权限、旋转屏幕或监控错误时请求恢复。
- 恢复操作会重读系统尺寸和缩放进行校验；失败时保留恢复记录并拒绝新的 Root 会话。
- 原始备份放在设备保护存储；开机、应用更新及新进程启动时尝试恢复遗留会话，**不会开机自动重新应用修改**。
- Root 每侧上限 15%，剩余短边至少 320 dp；暂只支持主用户、主显示器，以及当前逻辑尺寸等于物理尺寸的情况。已有较低分辨率时使用普通遮罩，不自动覆盖你的原有设置。
- 只使用 Android 的 `wm size` 和 `wm scaling`，不修改面板驱动、刷新率、DPI、系统分区或开机脚本。

## 实际限制

这些措施降低显示异常后难以恢复的风险，不能构成“不会出现黑屏、触摸错位或系统错误”的保证。校验使用系统报告的映射，仍须用户确认实际画面和触摸。OEM 显示投影、打孔和手势区域处理可能与 AOSP 不同；自动回退依赖系统命令可执行以及进程仍能调度。系统命令卡死时，独立进程也必须等当前命令返回才能串行恢复。重启、Root 授权撤销、ROM 杀死回退进程、系统服务卡死、应用卸载或清除数据均可能阻止回退。开机恢复也可能受 ROM 和 Root 管理器限制。

Root 模式旋转屏幕时会恢复原始尺寸并关闭会话，需要重新启用；普通遮罩仍支持自动横竖屏适配。圆角为视觉遮罩，不改变系统窗口的实际圆角形状。

## 备用恢复

优先等待自动回退，或使用应用底部按钮 / Root 前台通知中的“恢复原始显示”。如果无法操作界面，可通过此前已经启用并授权的 ADB 执行：

```bash
adb shell su -c 'wm size reset; wm scaling auto'
```

上述命令适用于原先使用默认尺寸、自动缩放的手机。若原来已有自定义尺寸或关闭了缩放，必须使用此前的 `WxH` 和原始缩放模式。例如：

```bash
adb shell su -c 'wm size 1080x2400; wm scaling off'
```

应用内“查看备用恢复方法”会根据当前备份生成对应命令。不要在未确认恢复时卸载应用、清除数据或撤销 Root。此应用不修改 DPI，不需要重置密度。

## 验证与构建

已通过 Android SDK 35 编译、APK 签名校验、49 项参数、映射和回退规则检查，以及 24 个独立修改与回退进程的模拟黑盒场景。测试直接覆盖居中映射成功、被拉伸成全屏的拒绝、映射偏移和矛盾、缩放命令失败或被忽略、原始 `off` 模式恢复、缩放恢复失败、无法读取缩放状态，以及此前的超时、续约、尺寸恢复和非法输入场景。测试使用模拟 `wm` / `dumpsys`，不能代替真机兼容性验证。

用 Android Studio 打开项目，或使用 JDK 17、Gradle 8.9、Android SDK Platform 35：

```bash
gradle :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。自己重新构建的 APK 签名可能不同，无法直接覆盖这里的安装包。签名密钥不包含在仓库中。

纯 Java 与模拟回退测试：

```bash
mkdir -p build/safety
javac -d build/safety app/src/main/java/com/edges/mask/DisplayProfile.java app/src/main/java/com/edges/mask/DisplayScaling.java app/src/main/java/com/edges/mask/DisplayProjection.java app/src/main/java/com/edges/mask/RootShell.java app/src/main/java/com/edges/mask/GuardScript.java tests/SafetyChecks.java
java -cp build/safety com.edges.mask.SafetyChecks
python3 tests/watchdog_checks.py build/safety
```

实现依据：Android AOSP 的 [WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/WindowManagerShellCommand.java)、[DisplayContent](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/DisplayContent.java)、[LogicalDisplay](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/LogicalDisplay.java)、[DisplayViewport](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/hardware/display/DisplayViewport.java) 与 Android 官方的 [系统栏适配说明](https://developer.android.com/develop/ui/views/layout/edge-to-edge)。厂商 ROM 仍须实际验证。
