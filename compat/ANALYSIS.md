# QTI framebuffer projection compatibility

AI-written analysis and implementation. The supplied ROM components were inspected as data; no supplied ELF was loaded or executed. Firmware binaries and the user's full diagnostic/build fingerprint are excluded from this repository.

## Evidence in the analyzed implementation

Analyzed `/system_ext/lib64/libsurfaceflinger.so` SHA-256:

`c8a05d1597feb9cd3578d803fa988399bd8f413ebaf871187eafadc0623af7aa`

These virtual addresses identify functions in that library only; the module does **not** patch or access these addresses:

| Function | ELF virtual address | Observed behavior |
| --- | --- | --- |
| `SurfaceFlinger::processDisplayChanged` | `0x43c760` | Calls the QTI display-change hook; skips ordinary `DisplayDevice::setProjection` if it returns true. |
| `QtiSurfaceFlingerExtension::qtiFbScalingOnDisplayChange` | `0x4a5960` | Queries feature 4, tests the internal display flag, invokes the framebuffer size/projection handler and returns true. |
| `QtiSurfaceFlingerExtension::qtiSetFrameBufferSizeForScaling` | `0x4a5214` | Computes size from `layerStackSpaceRect`. At `0x4a52f4`–`0x4a52fc`, copies its 16-byte rectangle to `orientedDisplaySpaceRect` unconditionally. Requests framebuffer size changes and applies these equal rectangles. |
| `QtiFeatureManager::qtiInit` | `0x4a8278` | Reads `vendor.display.enable_fb_scaling` with `GetBoolProperty(..., false)` and stores the cached Boolean at object offset `0x12` (`0x4a8514`–`0x4a851c`). |
| `QtiFeatureManager::qtiIsExtensionFeatureEnabled` | `0x4a92d8` | Feature 4 dispatches to `0x4a9344`, which returns the cached Boolean at offset `0x12`. Does not reread the property. |

The exported symbols and ARM64 instructions establish a path that explains the supplied measurements: logical/output/framebuffer all narrow together; an inset output projection is overwritten by the full logical rectangle; forcing GPU composition does not bypass the display-change hook. This is evidence for the overwrite path, not a trace proving that no other downstream component contributes to the physical stretch.

## Chosen repair

An optional root-manager module sets the **non-persistent, in-memory** property to false before SurfaceFlinger starts. It uses `resetprop -n` at `post-fs-data`, not `setprop`, and never kills or restarts SurfaceFlinger. Runtime property changes alone do not clear the cached flag. A normal reboot is required once after installation, and again after disabling/removing the module.

Both installer and boot script check the actual library hash. On an unknown/updated library, the module changes nothing. The boot script also leaves an already-running SurfaceFlinger alone, records a reason and exits. Hash checks and the property mutation have 3-second command limits; the script has no waiting loop or late process writer. It mounts no system files and has no SELinux rules, native hooks, persistent property writes, density/refresh-rate changes or panel controls.

With this hook inactive, the existing EdgeMask projection can keep the original output raster, for example 1440×3200, while mapping 1324×3200 content to `[58, 0, 1382, 3200]`. WM remains responsible for the input viewport; EdgeMask independently checks it and verifies compositor output before allowing confirmation. The legacy inverse-raster fallback remains available on other implementations and is not regarded as proof of physical correctness.

The module affects the firmware's framebuffer scaling for the whole boot, including outside EdgeMask. System resolution switching and power behavior may change. Restore the display, disable/remove the module in the root manager and reboot to revert. Removing the APK alone does not remove the module.

## Validation limits

`tests/framebuffer_module_checks.py` runs the actual boot/installer scripts against a temporary fake system. `tests/compositor_bridge_checks.py` runs the production Bridge with Android interface substitutes, including the overwrite failure and feature-disabled success. These tests cannot verify device boot ordering, native Binder execution, touch hardware or panel geometry. The supplied phone is not connected to this environment. Final acceptance requires an actual reboot, module-status check, on-device circle/square inspection, symmetric margins and touch checks.

Module packaging follows the [Magisk guide](https://topjohnwu.github.io/Magisk/guides.html) and [KernelSU guide](https://kernelsu.org/guide/module.html). Build with `python3 compat/build_module.py` before building the APK; the ZIP in `app/src/main/assets` is also delivered separately and exported from the app. Installation is never automatic.

## Installer compatibility correction (module 1.0.1 / APK 1.6.1)

The initial module 1.0 omitted the traditional `META-INF/com/google/android` installer entries. This is permitted by the current module documentation for manager-only ZIPs, but Magisk v24.3's actual manager requires those entries and reads `updater-script` before invoking the installer. The regression reproduces the missing-file exception behind its `Unzip error` message. The current Magisk manager instead copies its own `module_installer.sh` asset; its `Unzip error` can therefore also indicate a manager resource/cache failure independent of ZIP contents. Without the user's manager version, the screenshot alone does not distinguish these paths.

Module 1.0.1 includes the unmodified official `module_installer.sh` at `update-binary`, the `#MAGISK` marker, upstream attribution and GPL license; it forbids ZIP64 at packaging time. The boot/property script is unchanged. APK 1.6.1 additionally offers an explicitly user-invoked [Magisk CLI](https://topjohnwu.github.io/Magisk/tools.html#magisk) installation of this bundled module, bypassing manager UI asset extraction. It verifies Root, a running Magisk daemon, CLI availability and staged module files, reports failure details, prevents duplicate installs and never reboots automatically. Installation does not execute the module's boot script or change display parameters.

Pinned upstream: [Magisk 5f7b39ac631a63fa0af85ce4d099eaf78bb18d2c](https://github.com/topjohnwu/Magisk/tree/5f7b39ac631a63fa0af85ce4d099eaf78bb18d2c). Official source fixtures live under `tests/fixtures/magisk` with their own NOTICE. `tests/magisk_install_checks.py` runs the official bootstrap and `install_module()` after rebasing Android filesystem roots into a temporary test directory and stubbing platform setup; it also exercises the production Java installer/RootShell and shell launcher with fake `su`/Magisk. These tests cannot prove that the phone's installed Magisk daemon or boot timing works.
