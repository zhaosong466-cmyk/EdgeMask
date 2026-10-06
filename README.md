# EdgeMask · 对称黑边 1.1

一个轻量 Android 屏幕遮罩工具：左右对称黑条、可调圆角，以及可选的 **Root 系统显示同步**。

**AI 编写声明：源码、项目说明与 APK 由 ChatGPT / Codex 根据用户需求生成。Root 功能为实验功能，尚未经真机测试；不能保证所有 Android 版本、Root 管理器和厂商 ROM 均稳定。**

## 下载与使用

安装 [EdgeMask-1.1.apk](apk/EdgeMask-1.1.apk)，Android 8.0 及以上。与此前的 1.0 使用相同签名，可覆盖更新。

1. 授予无障碍服务或“在其他应用上层显示”权限，选择对应显示方式。
2. 普通模式直接点击“启用 / 应用当前设置”，调节宽度和圆角。
3. 如需系统重新排版，点击“检测并申请 Root 权限”，在 Root 管理器中手动允许。
4. 打开“Root 同步系统显示”开关，首次建议每侧 **1%**，再点击启用。
5. 每次显示尺寸改变后，检查画面、键盘及触摸是否正常，在 **15 秒内点击确认并保留**；不确认会自动回退。
6. 停止使用时点击页面顶部“关闭遮罩并恢复原始显示”，确认恢复成功后再卸载或撤销 Root。

## 功能

- 普通遮罩每侧宽度 0–25%，左右同步；圆角半径 0–160 dp。
- 滑块实时调整，±1 微调；宽度改变在 Root 模式下合并为约 450 ms 一次，避免每个拖动事件都重配置系统。
- 普通模式只遮住内容，不修改布局；Root 模式修改默认显示器的 **逻辑显示尺寸**，使应用重新排版，DPI 不修改。
- Root 模式由系统显示投影产生两侧留黑，因此停用额外的侧边遮罩，只保留圆角遮罩，避免重复遮挡。
- 默认不使用 Root，没有 Root 也可使用全部原有遮罩功能。
- 原生 Java，无第三方运行时依赖、无网络权限。

| 显示方式 | 权限 | 行为 |
| --- | --- | --- |
| 无障碍遮罩 | 开启本应用无障碍服务 | 触摸穿透；不读取窗口内容、不订阅事件、不执行手势 |
| 普通悬浮窗 | 在其他应用上层显示 | 黑条和圆角方块内拦截触摸；中央正常操作 |
| Root 同步（附加选项） | Root + 上述任一种遮罩权限 | 系统按目标区域重新排版，仍由遮罩绘制圆角 |

## Root 回退保护

- 修改前备份完整的原始逻辑尺寸，并记录此前是否已经存在自定义尺寸。
- 已有尺寸覆盖会恢复为原来的 `WxH`；原先没有尺寸覆盖才执行 `wm size reset`。
- 先验证独立回退进程已经运行，再执行尺寸变更；回退进程启动失败时不修改显示。
- 会话中的尺寸修改与恢复由同一个独立进程串行执行，避免延迟的修改命令在恢复之后再次生效。
- 每次变更必须重新确认。拖动期间不会无限延长同一次 15 秒预览期限。
- 确认后由前台服务续约；应用进程被终止或心跳丢失时，独立回退进程在正常调度下约 20 秒内尝试恢复。
- 手动关闭、移除应用任务、撤销遮罩权限、旋转屏幕或监控错误时请求恢复。
- 恢复操作会重读系统尺寸进行校验；失败时保留恢复记录并拒绝新的 Root 会话。
- 原始备份放在设备保护存储；开机、应用更新及新进程启动时尝试恢复遗留会话，**不会开机自动重新应用修改**。
- Root 每侧上限 15%，剩余短边至少 320 dp；不支持副用户、副显示器、关闭自动缩放或原始宽高比已经被改动的情况。
- 只使用 Android 的 `wm size` 覆盖，不修改面板驱动、刷新率、DPI、系统分区或开机脚本。

## 实际限制

这些措施降低显示异常后难以恢复的风险，不能构成“不会出现黑屏、触摸错位或系统错误”的保证。OEM 显示投影、打孔和手势区域处理可能与 AOSP 不同；自动回退依赖系统命令可执行以及进程仍能调度。重启、Root 授权撤销、ROM 杀死回退进程、系统服务卡死、应用卸载或清除数据均可能阻止回退。开机恢复也可能受 ROM 和 Root 管理器限制。

Root 模式旋转屏幕时会恢复原始尺寸并关闭会话，需要重新启用；普通遮罩仍支持自动横竖屏适配。圆角为视觉遮罩，不改变系统窗口的实际圆角形状。

## 备用恢复

优先等待自动回退，或使用应用顶部按钮 / Root 前台通知中的“恢复原始显示”。如果无法操作界面，可通过此前已经启用并授权的 ADB 执行：

```bash
adb shell su -c 'wm size reset'
```

这会恢复默认尺寸。若原来已有自定义尺寸，使用此前的 `WxH`：

```bash
adb shell su -c 'wm size 1080x2400'
```

不要在未确认恢复时卸载应用、清除数据或撤销 Root。此应用不修改 DPI，通常不需要重置密度。

## 验证与构建

已通过 Android SDK 35 编译、APK 签名校验、26 项参数和回退规则检查，以及 17 个独立修改与回退进程的模拟黑盒场景。测试覆盖超时/应用失联、原有覆盖尺寸、主动停止、心跳续约、损坏的续约文件、恢复重试与失败、实际目标应用、连续目标更新、延迟修改、修改失败，以及过度收窄、不足 320 dp、同时改变两轴、放大、非法输入和前导零的拒绝。测试使用模拟 `wm`，未在真实手机执行系统修改，不能代替真机兼容性验证。

用 Android Studio 打开项目，或使用 JDK 17、Gradle 8.9、Android SDK Platform 35：

```bash
gradle :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。自己重新构建的 APK 签名可能不同，无法直接覆盖这里的安装包。签名密钥不包含在仓库中。

纯 Java 与模拟回退测试：

```bash
mkdir -p build/safety
javac -d build/safety app/src/main/java/com/edges/mask/DisplayProfile.java app/src/main/java/com/edges/mask/RootShell.java app/src/main/java/com/edges/mask/GuardScript.java tests/SafetyChecks.java
java -cp build/safety com.edges.mask.SafetyChecks
python3 tests/watchdog_checks.py build/safety
```

实现依据：Android AOSP 的 [WindowManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/WindowManagerShellCommand.java) 与 [LogicalDisplay](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/LogicalDisplay.java)。厂商 ROM 仍须实际验证。
