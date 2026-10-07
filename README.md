# EdgeMask · 对称黑边 1.6.1

Android 屏幕工具：左右对称黑条、可调宽度与内边框圆角；可选 Root 收窄系统布局，并将内容投影到屏幕中央。

**AI 编写声明：应用、自编模块脚本和说明由 ChatGPT / Codex 根据用户需求编写并构建。模块使用的官方 Magisk 安装入口保留上游来源和 GPL v3 许可。**

## 1.6.1：修复面具模块安装入口，增加命令安装

1.0 模块 ZIP 完整性校验虽然通过，但遗漏了传统 `META-INF/com/google/android/update-binary` 与 `updater-script`。Magisk v24.3 的管理器源码会先解包这个目录并读取 `#MAGISK` 标记，旧包因此触发 `Unzip error`。当前 Magisk 管理器的同一错误文字也可能来自读取其 APK 自带安装脚本的失败；用户尚未提供面具版本，不能把截图的唯一原因确定为缺少入口。

配套模块 **1.0.1** 补入未修改的官方安装脚本与 `#MAGISK` 标记，使用普通 ZIP 格式，并附上来源和许可。APK **1.6.1** 更新内置 ZIP，并增加“通过面具安装”：用户点击后授予 Root，应用仅将内置模块交给已安装 Magisk 的官方 `--install-module` 命令，绕过管理器的解包/资源读取界面。安装后检查暂存模块 ID、版本和必需文件；失败显示可复制的完整结果。没有自动重启，也不会在安装时改当前显示或缩放属性。非 Magisk 用户仍可保存 ZIP 后交给对应管理器安装。

建议操作：

1. 在旧版恢复原始显示，等待恢复完成；覆盖安装 [EdgeMask-1.6.1.apk](apk/EdgeMask-1.6.1.apk)，保留原签名。
2. 点击“修复分辨率修改后被拉伸（Root 模块）”→“通过面具安装”，授予 Root。只有安装成功才手动重启一次。
3. 重启后点击“查看修复模块状态（只读）”，再用每侧 1% 验证手机画面及触摸。宽度、圆角仍在 APK 中实时调整。

也可直接在面具中选择 [EdgeMask-QTI-Compat-1.0.1.zip](app/src/main/assets/EdgeMask-QTI-Compat-1.0.1.zip)，已经装了 APK 1.6 时无需先升级 APK 才能安装此 ZIP。若面具界面仍显示相同错误，使用 APK 1.6.1 的命令安装入口。安装失败无需为此重启；失败结果可以复制给开发者，应用不会自动发送。

验证包含旧版管理器缺少入口的失败复现、官方安装引导及实际 `install_module()` 在临时文件系统中的安装，以及生产 Java/RootShell/命令脚本的成功、授权拒绝、非 Root、缺少面具命令、守护进程不可用、版本不明、不支持 CLI、安装器错误、未知库、虚假成功、错误版本、不完整文件、缺文件、路径引号/命令注入和重复安装检查。12 个原有模块场景与 18 个安装入口/CLI 场景通过；安装测试没有执行显示、属性修改或重启命令。显示修改、回退和开机属性脚本沿用 1.6；仍未连接真机验证面板或启动时序。

上游来源及许可见模块的 [NOTICE](compat/qti-framebuffer/NOTICE)、[LICENSE-Magisk](compat/qti-framebuffer/LICENSE-Magisk) 和 [测试夹具说明](tests/fixtures/magisk/NOTICE)。官方 CLI 文档见 [Magisk Tools](https://topjohnwu.github.io/Magisk/tools.html#magisk)。

## 1.6：关闭 ROM 覆盖投影的帧缓冲缩放路径

实际 ROM 组件的静态分析找到了与 1.5 读回结果一致的处理路径：`qtiFbScalingOnDisplayChange` 在 framebuffer scaling 启用时接管内置屏幕投影；`qtiSetFrameBufferSizeForScaling` 用逻辑矩形覆盖输出矩形，并根据逻辑尺寸调整输出缓冲。因此 `[53, 0, 1271, 3200]` 被改回 `[0, 0, 1324, 3200]`；开启 GPU 合成不会关闭该路径。分析方法和坐标关系见 [兼容说明](compat/ANALYSIS.md)。

该开关从 `vendor.display.enable_fb_scaling` 读取后缓存在 SurfaceFlinger 初始化对象中。1.6 配套模块在正常开机的早期将这一**内存属性**置为 `false`，使 QTI 覆盖路径不再执行，让原有居中投影保留完整输出缓冲。**需要安装模块并重启一次；仅覆盖安装 APK 或运行中改属性无法清除进程内缓存。** 此后宽度和圆角仍在应用里实时调节，无需逐次重启。

使用方法：

1. 在旧版点击“恢复原始显示并关闭遮罩”，等待恢复完成。
2. 覆盖安装 [EdgeMask-1.6.apk](apk/EdgeMask-1.6.apk)，与旧版使用相同签名。
3. 在 Magisk / KernelSU 管理器中从本地安装已修正入口的 [EdgeMask-QTI-Compat-1.0.1.zip](app/src/main/assets/EdgeMask-QTI-Compat-1.0.1.zip)。也可在 APK 1.6.1 内点击“修复分辨率修改后被拉伸（Root 模块）”保存同一 ZIP。安装完**手动重启一次**。
4. 回到应用，检测 Root、打开 Root 同步，先设每侧 1% 并启用。直接检查手机的圆形、两侧边距与触摸，正常再确认；可用“查看模块状态”确认本次开机是否执行。若只启用遮罩，系统布局不会收窄。
5. 撤回模块：先在应用恢复原始显示，再在 Root 管理器中停用/移除模块并重启。卸载 APK 不会移除模块。

模块没有 `system` 挂载、`system.prop`、晚启动脚本、SELinux 规则或原生注入。安装期间只做库校验；开机脚本使用有时限的 `resetprop -n` 设置一个非持久属性，不重启显示服务，不操作面板或 WM。每次开机核对已分析显示库的 SHA-256，OTA 改变实现时会跳过；如果显示服务已经启动也跳过，记录原因。校验针对组件实现，APK 没有机型禁用名单。停用后正常重启由 ROM 加载原属性值。

新增模块脚本场景和生产 Bridge 模拟回归：复现 QTI 覆盖导致失败，关闭该路径后验证原始输出缓冲与居中投影，GPU 兼容勾选开/关均可使用普通居中路径。**静态分析和模拟检查支持这一修复方向，尚未连接手机验证开机时序、实际面板和触摸。不能保证所有显示模式、视频内容或 ROM 均正常。** 模块长期关闭厂商帧缓冲缩放，可能影响系统自己的分辨率切换或功耗；如有异常恢复原始显示并停用模块重启。原有倒计时、独立 watchdog、旋转恢复、心跳及输入/输出校验全部保留。

导出的 ROM 二进制、完整构建指纹和用户诊断不会加入仓库。

## 1.5.1：取得 ROM 显示组件，继续定位投影不生效

**该版新增只读导出，尚未修复真机横向拉伸。Root 显示修改与回退路径沿用 1.5；没有新增显示写入，也没有按机型禁用功能。**

1.5 的真机报告补齐了关键证据：修改后逻辑区、输出区、GPU 帧缓冲均为 1324×3200；请求恢复 1440×3200 输出缓冲后尺寸未改变；GPU 标志由 0 变为 1，但目标投影 `[53, 0, 1271, 3200]` 连续五次读回仍是 `[0, 0, 1324, 3200]`。由此能确认请求未在输出几何中生效，不能确定厂商是丢弃请求还是随后覆盖，也不能把模拟成功称作真机修复。

为分析实际 ROM 提供一键导出：

1. 在旧版恢复原始显示，等待恢复完成后覆盖安装 [EdgeMask-1.5.1.apk](apk/EdgeMask-1.5.1.apk)，保留原签名。
2. 点击“导出 ROM 显示组件（只读）”→“生成 ZIP”，按提示授予 Root。
3. 在系统文件保存界面保存 `EdgeMask-ROM-evidence.zip`。可用文件管理器查看清单，再自行把 ZIP 发到当前对话；无需再次修改分辨率。

导出包含允许列表中的系统显示组件，例如 `libgui.so`、`libandroid_runtime.so`、SurfaceFlinger 相关库、SurfaceFlinger 可执行程序，以及可取得且不超限的 `framework.jar`。附有系统版本/机型、现有数值显示诊断、文件尺寸与 SHA-256。只在指定系统库目录匹配 SurfaceFlinger 文件名；不读取相册、聊天、账号、应用数据或 logcat，不自动上传。拒绝解析到 `/data`、共享存储或范围外位置的路径。单组件不超过 16 MiB，原始组件合计不超过 28 MiB；缺失、无法读取或超限的组件会记录在清单中。

本版通过 42 项导出路径、大小边界和二进制完整性检查；安卓源码仍编译并使用原发行密钥签名。未连接安卓真机，Root 读取与系统文件保存界面的实际表现仍需验证。导出的厂商二进制不纳入 GitHub 仓库。

## 1.5：适配修改尺寸后的输出比例变化

1.4.1 的新诊断表明：原始合成状态已成功备份，但修改 WM 尺寸后触发“当前屏幕输出比例与原始显示不一致”，因此 GPU 设置与居中投影尚未执行。旧版没有保存这一检查之前的输出尺寸，不能据此确定厂商具体改成了多大。

本版针对“输出缓冲也随逻辑尺寸变窄，随后铺满原面板”的可能路径增加适配：

- 始终保留修改前的完整输出尺寸作为参考。优先用 `setDisplaySize` 请求恢复已备份的输出缓冲尺寸，并读取结果；ROM 可能忽略物理显示的这一请求，不把调用成功当作生效。
- 若输出仍符合新的逻辑比例，则在 GPU 合成中反向补偿，使输出缓冲铺满原屏幕后，内容回到预期宽度，两边保留对称边距。只有输出比例符合原始比例或目标逻辑比例才继续，其他不明变化仍会恢复。
- 同时检查逻辑区域、原生输出矩形、旋转、GPU 帧缓冲与原始屏幕之间的完整坐标换算。反向补偿需要勾选 GPU 合成兼容模式；普通居中路径可关闭该模式。
- 在比例校验前保存 `after-wm` 数值，之后保存缓冲尺寸请求和映射结果。失败或超时后仍可复制诊断。
- 新增的缓冲尺寸请求先写入恢复记录；恢复时独立尝试 GPU、缓冲尺寸和全屏投影，某一步失败仍尝试其他步骤。

例：原屏幕 1440×3200、目标 1354×3200。如果输出也变为 1354×3200，预先将内容压入 `[40, 0, 1314, 3200]`；假设该缓冲随后铺满原屏幕，显示内容宽约 1355 像素，误差来自整数像素取整。这是补偿模型示例，**不是本次诊断中已经取得的修改后尺寸**。

**未连接该型号真机，尚不能宣布物理拉伸已解决。** 软件读回和模拟检查不能证明面板最终比例；反向补偿也依赖 ROM 的触摸坐标转换。首次务必直接检查手机上的圆形、两侧边距和不同位置的触摸，异常时恢复。没有按机型或系统版本禁用 Root。

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

先在旧版点击“恢复原始显示并关闭遮罩”，再覆盖安装 [EdgeMask-1.6.1.apk](apk/EdgeMask-1.6.1.apk)。与旧版使用相同签名。当前反馈 ROM 的拉伸修复需按 1.6.1 步骤安装兼容模块并重启；以下是遮罩和 Root 功能的操作方法。

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

修改前保存原始尺寸、覆盖状态、缩放模式、内置屏幕 ID、投影基线、缓冲尺寸、GPU 合成原状态和启动标识。先启动独立回退进程，准备好原始记录后才能修改。尺寸、缩放、合成层操作以及恢复均由同一监控进程串行执行，避免应用失联后的迟到修改。

恢复时依次尝试原始尺寸、原始缩放、原始 GPU 设置、已请求修改的缓冲尺寸及全屏投影；某一步失败不跳过其他恢复步骤。恢复发生在旋转之后时，使用当前方向恢复全屏，不套用旧竖屏矩形。系统已重启时，不重放上次启动的临时合成设置。

- 未确认超时、关闭、任务移除、权限失效、旋转或监控失败时恢复。
- 确认后约每秒续约，心跳丢失后正常调度下约 20 秒触发恢复；约每 5 秒检查输出与输入状态。
- 合成辅助进程每次调用设 8 秒上限。WM 命令仍依赖系统服务返回，不能保证系统卡死时按时恢复。
- 恢复最多尝试三次；未验证成功保留备份，避免新会话覆盖恢复信息。
- 更新、开机及应用启动时尝试恢复，不自动重新应用 Root 收窄。

Root 输出适配不能保证所有 ROM 无显示或触摸异常。不要在恢复确认前清除应用数据、卸载或撤销 Root；这些操作可能移除或阻止恢复。APK 未改 DPI、刷新率、面板驱动、系统分区或 SELinux。可选兼容模块有单独的开机脚本，只在已分析实现上覆盖一个内存中的厂商缩放属性。

### 备用恢复

优先用应用底部或通知中的恢复按钮。应用内“查看备用恢复方法”按本次备份生成 Root 终端命令，包含合成层恢复步骤。

原本默认尺寸与自动缩放时，基本尺寸恢复命令为：

```sh
wm size reset
wm scaling auto
```

若本次启用了合成层修正，还应运行应用给出的 `compositor.sh restore` 命令，以恢复原有 GPU 状态与投影。不要一律把 GPU 标志关闭：它在启用本应用前可能就已开启。DPI 未改，不需要重置密度。

## 构建与验证

已用 Android SDK 35、Java 17 编译并用原发行证书签名。1.6 通过 12 个模块安装/启动/退出场景及 10 个生产 Bridge 的 JVM 模拟场景；原有 53、30、100、42 项纯逻辑检查和 24、10 个监控脚本场景也通过。以下测试均不代表真机验证。

1.5 验证包括 53 项原有参数/映射/诊断检查、30 项输出几何回归、100 项补偿检查（含 44 个方向/宽度场景）。1.5 新增 7 个实际 `CompositorBridge` 主程序的 JVM 模拟场景，验证缓冲请求生效/被忽略、部分尺寸修改后失败、投影修改后失败、原 GPU 已开启、旋转恢复、缓冲恢复失败仍恢复投影及后续重试。

另运行 24 个原有回退和 10 个合成层回退黑盒场景。黑盒测试运行实际生成的监控脚本，用模拟 WM、dumpsys 和合成接口检查顺序与恢复；不会执行真正的 Root 操作。Bridge 测试仅替换临时副本中的 dumpsys 可执行路径，Android 接口由 JVM 测试替身实现。这些测试不覆盖真实 Binder、触摸硬件或物理面板效果。

Android Studio，或 JDK 17、Gradle 8.9、Android SDK Platform 35：

```sh
gradle :app:assembleDebug
```

输出 `app/build/outputs/apk/debug/app-debug.apk`。自行构建的签名可能不同，发行签名密钥不在仓库。

纯 Java 与监控脚本测试：

```sh
mkdir -p build/safety
javac -d build/safety app/src/main/java/com/edges/mask/DisplayProfile.java app/src/main/java/com/edges/mask/DisplayScaling.java app/src/main/java/com/edges/mask/DisplayProjection.java app/src/main/java/com/edges/mask/CompositorGeometry.java app/src/main/java/com/edges/mask/CompositorPlan.java app/src/main/java/com/edges/mask/DiagnosticGeometry.java app/src/main/java/com/edges/mask/RootShell.java app/src/main/java/com/edges/mask/GuardScript.java tests/SafetyChecks.java tests/CompositorChecks.java tests/CompositorPlanChecks.java
java -cp build/safety com.edges.mask.SafetyChecks
java -cp build/safety com.edges.mask.CompositorChecks
java -cp build/safety com.edges.mask.CompositorPlanChecks
python3 tests/watchdog_checks.py build/safety
python3 tests/compositor_guard_checks.py build/safety
python3 tests/compositor_bridge_checks.py
python3 compat/build_module.py
python3 tests/framebuffer_module_checks.py
python3 tests/magisk_install_checks.py
# 只读 ROM 导出检查
javac -d build/safety app/src/main/java/com/edges/mask/RootShell.java app/src/main/java/com/edges/mask/RomEvidence.java tests/RomEvidenceChecks.java
java -cp build/safety com.edges.mask.RomEvidenceChecks
```

模块机制依据：[Magisk 开发指南](https://topjohnwu.github.io/Magisk/guides.html)与 [KernelSU 模块指南](https://kernelsu.org/guide/module.html)。

实现依据：[AOSP SurfaceControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/view/SurfaceControl.java)、[DisplayControl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/DisplayControl.java)、[SurfaceFlinger](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/surfaceflinger/SurfaceFlinger.cpp)、[Output](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/surfaceflinger/CompositionEngine/src/Output.cpp)、[LogicalDisplay](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/display/LogicalDisplay.java)。Root 类加载方式参考 [scrcpy 的 DisplayControl 封装](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java)。GPU 兼容路径仅使用 AOSP 已定义的 1010 查询和 1008 设置协议，先验证接口描述符、完整回复格式并备份原值，不猜测厂商私有命令。
