# EdgeMask · 对称黑边 1.4.1

Android 屏幕工具：左右对称黑条、可调宽度与内边框圆角；可选 Root 收窄系统布局，并将内容投影到屏幕中央。

**AI 编写声明：源码、说明和 APK 由 ChatGPT / Codex 根据用户需求编写和构建。**

## 1.4.1：修复合成层坐标解析错误

用户提供的诊断显示，1.4 在读取原始合成坐标时抛出“ROM 合成层坐标格式不同”，修正记录为空。用诊断中的 `Rect(0, 0, 1440, 3200)` 坐标行及其行尾空格，已在本地复现相同异常：解析器使用完整字符串匹配，却未处理 `}` 后的空白，导致防拉伸修正在准备阶段被阻断。

- 容许合成字段周围及行尾的空格、制表符，支持 CRLF；坐标、方向、字段完整性与居中检查保持有效。
- 新增 8 项回归，覆盖这份诊断的格式、正确居中后的读回、无效坐标、错误方向、异常后缀和重复记录。测试只保留坐标格式，显示 ID 是合成值，不包含用户的系统指纹。
- 这是解析热修复：修复前用该格式的回归测试失败，修复后 30 项合成几何与 53 项原有纯逻辑检查通过。没有再次修改 Root 投影、GPU 设置或回退流程。

**1.4 的此次诊断反映的是功能未能启动，不能据此判断 GPU / 居中投影方案的实际效果。1.4.1 仍需在手机上确认画面和触摸。**

## 1.4：修正输出路径，恢复小米 13 Ultra 的 Root 功能

针对“小米 13 Ultra / Android 16，修改分辨率后实际画面横向拉伸，但截图比例正常”的反馈，本版撤掉 1.3 的机型禁用名单及按系统构建记录的禁用状态。反馈拉伸只结束当前会话，后续仍可重新启用。

本版新增实际的合成层操作：

1. 用 `wm size` 收窄系统布局、`wm scaling off` 关闭框架自动缩放，保留 DPI。
2. 由独立 Root 进程调用 `SurfaceControl.Transaction.setDisplayProjection`，明确指定逻辑内容和居中输出矩形。输出尺寸来自当前 SurfaceFlinger 的内置屏幕，避免仅把 `wm size` 的物理尺寸当作最终输出尺寸。
3. 默认勾选 **GPU 合成兼容模式**，会话中临时强制客户端合成，尝试绕过硬件合成的二次拉伸路径。关闭该选项仍保留显式居中投影。GPU 模式可能增加耗电，并影响部分视频/受保护内容；退出时恢复原有 GPU 合成设置。
4. 同时检查输入映射、SurfaceFlinger 输出矩形、帧缓冲比例及 GPU 标志。软件读回仍不能证明最终面板比例，保留手机实际画面与触摸确认。

例如原始 1440×3200、每侧 3% 时，逻辑尺寸为 1354×3200，1440×3200 输出模式下目标矩形是 `[43, 0, 1397, 3200]`，两侧各留 43 像素。若实际输出是同纵横比的 1080×2400，则按其尺寸换算，保持横纵缩放一致。

**合成层适配代码已实现、编译并通过模拟回归；1.4 真机诊断暴露的解析问题已在 1.4.1 修复，实际消除拉伸的效果尚待真机确认。** “输入报告正确而实际输出拉伸”的精确厂商原因尚未取得真机证据，不能把这一修正称为已完成真机验证。

## 下载与使用

先在旧版点击“恢复原始显示并关闭遮罩”，再覆盖安装 [EdgeMask-1.4.1.apk](apk/EdgeMask-1.4.1.apk)。与 1.0–1.4 使用相同签名。

1. 选择无障碍遮罩或普通悬浮窗，授予相应权限。
2. 点击“检测并申请 Root 权限”，开启 Root 同步；保留“GPU 合成兼容模式”勾选。
3. 首次设每侧 **1%**，点击“启用 / 应用当前设置”。点击“检查手机画面并确认”，直接看手机上的圆形、正方形、两侧黑边与触摸位置；正常再保留。
4. 首次会话倒计时 30 秒（包含合成接口准备时间），已确认后的新修改为 25 秒。不确认、点击恢复、旋转或会话失联均会尝试恢复。
5. 若仍拉伸，点击“画面被拉伸：恢复并保留黑边”，再点“复制显示诊断”。最近一次修改前后的纯数值合成记录会保留到下一次会话，超时恢复后也可复制，无需靠截图展示物理拉伸。

非 Root 模式仍支持 Android 8.0+。增强 Root 路径依赖 ROM 提供可读取的 SurfaceFlinger 几何数据及隐藏显示接口；接口准备失败会给出具体错误，按能力判断，不按机型封禁。

## 功能与权限

- 普通黑边每侧 0–25%，左右同步；内圆角 0–160 dp；滑块和 ±1 按钮。
- Root 每侧最多 15%，可见短边至少 320 dp，按原尺寸/DPI/方向计算上限。1440×3200、DPI 560 竖屏最多每侧 11%。圆角仍由遮罩绘制。
- Root 只修改主用户、唯一可识别的内置主屏幕；原始逻辑尺寸须等于 WM 物理尺寸，原始输出为全屏且同纵横比。当前屏幕安装方向须为默认方向。
- 原生 Java，无第三方运行时依赖，无网络权限，默认关闭 Root。

| 方式 | 权限 | 行为 |
| --- | --- | --- |
| 无障碍遮罩 | 本应用无障碍服务 | 触摸穿透；不读取窗口文字、不订阅事件、不执行手势 |
| 普通悬浮窗 | 在其他应用上层显示 | 黑边和圆角方块内拦截触摸；中央正常操作 |
| Root 同步 | Root + 任一种遮罩权限 | 重排布局、显式居中投影；可选 GPU 兼容模式 |
| 只读诊断 | Root | 读取系统构建、显示参数和最近一次合成记录，用户自行复制；不截图、不自动上传 |

## 恢复机制

修改前保存原始尺寸、覆盖状态、缩放模式、内置屏幕 ID、投影基线、GPU 合成原状态和启动标识。先启动独立回退进程，准备好原始记录后才能修改。尺寸、缩放、合成层操作以及恢复均由同一监控进程串行执行，避免应用失联后的迟到修改。

恢复时依次尝试原始尺寸、原始缩放、原始 GPU 设置及全屏投影；某一步失败不跳过其他恢复步骤。恢复发生在旋转之后时，使用当前方向恢复全屏，不套用旧竖屏矩形。系统已重启时，不重放上次启动的临时合成设置。

- 未确认超时、关闭、任务移除、权限失效、旋转或监控失败时恢复。
- 确认后约每秒续约，心跳丢失后正常调度下约 20 秒触发恢复；约每 5 秒检查输出与输入状态。
- 合成辅助进程每次调用设 8 秒上限。WM 命令仍依赖系统服务返回，不能保证系统卡死时按时恢复。
- 恢复最多尝试三次；未验证成功保留备份，避免新会话覆盖恢复信息。
- 更新、开机及应用启动时尝试恢复，不自动重新应用 Root 收窄。

Root 输出适配不能保证所有 ROM 无显示或触摸异常。不要在恢复确认前清除应用数据、卸载或撤销 Root；这些操作可能移除或阻止恢复。未改 DPI、刷新率、面板驱动、厂商属性、系统分区、SELinux 或开机脚本。

### 备用恢复

优先用应用底部或通知中的恢复按钮。应用内“查看备用恢复方法”按本次备份生成 Root 终端命令，包含合成层恢复步骤。

原本默认尺寸与自动缩放时，基本尺寸恢复命令为：

```sh
wm size reset
wm scaling auto
```

若本次启用了合成层修正，还应运行应用给出的 `compositor.sh restore` 命令，以恢复原有 GPU 状态与投影。不要一律把 GPU 标志关闭：它在启用本应用前可能就已开启。DPI 未改，不需要重置密度。

## 构建与验证

已用 Android SDK 35、Java 17 编译。本次通过 53 项原有参数/映射/诊断检查、30 项输出几何回归。未变更的回退流程沿用 1.4 已通过的 24 个原有回退和 10 个合成层回退黑盒场景。黑盒测试运行实际生成的监控脚本，用模拟 WM、dumpsys 和合成接口检查顺序与恢复；不会执行真正的 Root 操作。没有连接安卓真机，隐藏 API / ROM 硬件路径需要实测。

Android Studio，或 JDK 17、Gradle 8.9、Android SDK Platform 35：

```sh
gradle :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。自行构建的签名可能不同，发行签名密钥不在仓库。

纯 Java 与监控脚本测试：

```sh
mkdir -p build/safety
javac -d build/safety app/src/main/java/com/edges/mask/DisplayProfile.java app/src/main/java/com/edges/mask/DisplayScaling.java app/src/main/java/com/edges/mask/DisplayProjection.java app/src/main/java/com/edges/mask/CompositorGeometry.java app/src/main/java/com/edges/mask/DiagnosticGeometry.java app/src/main/java/com/edges/mask/RootShell.java app/src/main/java/com/edges/mask/GuardScript.java tests/SafetyChecks.java tests/CompositorChecks.java
java -cp build/safety com.edges.mask.SafetyChecks
java -cp build/safety com.edges.mask.CompositorChecks
python3 tests/watchdog_checks.py build/safety
python3 tests/compositor_guard_checks.py build/safety
```

实现依据：[AOSP SurfaceControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/view/SurfaceControl.java)、[DisplayControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/DisplayControl.java)、[SurfaceFlinger](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/surfaceflinger/SurfaceFlinger.cpp)、[Output](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/surfaceflinger/CompositionEngine/src/Output.cpp)、[LogicalDisplay](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/LogicalDisplay.java)。Root 类加载方式参考 [scrcpy 的 DisplayControl 封装](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java)。GPU 兼容路径仅使用 AOSP 已定义的 1010 查询和 1008 设置协议，先验证接口描述符、完整回复格式并备份原值，不猜测厂商私有命令。
