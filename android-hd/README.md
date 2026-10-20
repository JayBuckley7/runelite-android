# 117 HD on Android

This module builds 117 HD's ZoneRenderer against the Android GLES 3.2 bridge.
The upstream checkout is `third-party/rlhd`, based on commit
`f5be256078b5e398089aa73e48116c7f95dc329f` of `117HD/RLHD` (1.6.0), selected from the official RuneLite Plugin Hub on 2026-10-19.
The vendored source retains its license and contains Android Java and GLSL adaptations.
117 HD is disabled by default; enable it in Plugins to select the experimental renderer.

The host integration is `AndroidHdHost`; the LWJGL-compatible API and native
memory/PBO functions are under `android/src/androidMain/java/org/lwjgl` and
`android/src/androidMain/cpp`. AndroidTextureScaler bypasses the AWT shim's
incomplete affine image operations, with bicubic resampling and RGBA upload.

## Build and verify

As with the base Android build, put the version-matching official injected-client
JAR under `data/` using the filename selected by `target` in `android/build.gradle.kts`.
It is an ignored build input, not part of this source snapshot.

With JDK 21, the Android SDK, NDK 27.1.12297006 and CMake 3.22.1 configured, run:

```powershell
.\gradlew.bat :android:assembleDebug --console=plain
```

The APK is `android/build/outputs/apk/debug/android-debug.apk`. Shader variants can be
expanded with `:android-hd:dumpGlesShaderProbes`. The standalone GLES probe source is
`tools-src/hd_gles_probe.c`; it takes vertex and fragment shader file paths.
Host regression checks are `TestHdTextureScaler`, `TestHdMemoryStack`, and
`VerifyHdAwtBindings` under `tools-src`.

## 1.6 integration checks

- Full debug APK build, texture scaling and memory-stack regressions, mobile-panel
  regression, and bytecode audit against Android AWT and LWJGL bridge classes.
- 99 shader compile/link pairs on Pixel Fold / Mali-G710, including UI pixel
  readback. Coverage includes scene, water, sky, point/triangle stars, nebula bake,
  terrain shadows, gap fill, tiled lighting, UI and calibration; feature variants
  exercise parallax, day/night, transparency, PCF, dither and PCSS shadows.
- The same 99 pairs also pass on Pixel 10 Pro Fold / PowerVR D-Series using
  vertex SSBOs (198 pairs across the two devices); UI pixel readback passes there too.
- Face data uses vertex SSBOs only when the driver reports support. Mali-G710 on
  the tested Fold reports zero vertex SSBO slots and uses its texture buffer;
  the storage-buffer route remains available for GPUs with small texture limits.
- Old tiled-lighting and image-store boolean preferences migrate to ON/OFF.

Run the standalone driver check without installing or restarting the game:

```powershell
.\Test-HdShaders.ps1 -Device <adb-serial>
# For a GPU with vertex SSBO support, exercise the path used at runtime:
.\Test-HdShaders.ps1 -Device <adb-serial> -FaceStorageBuffer
```

The device must have the compiled `tools-src/hd_gles_probe.c` at
`/data/local/tmp/hd_gles_probe`. Its capability report includes
`max_vertex_shader_storage_blocks`. Logs and results go to `mobile-ganom/`.
Shader checks do not prove complete scene rendering or gameplay stability.
The 1.6 APK has not yet been installed or verified in-game.

## Historical 1.5.2 gameplay checks on Pixel Fold / Mali-G710

- Actual in-game HD terrain, textures, characters and UI in MTA Enchantment Room.
- Scene redraw after sidebar resize and Android surface recreation on resume.
- 24 shader compile/link combinations on the phone GPU; UI PBO upload and pixel readback.
- Texture mirror, RGBA channel order, transparent replacement, and bicubic scaling
  compared against desktop AWT; pooled-buffer capacity regression; AWT ABI audit.

## Current limits

This is an initial mobile port. Observed gameplay performance in the tested room
was about 10–20 FPS, not a sustained benchmark. Mobile defaults use 75% scene
resolution, 2x MSAA, detailed shadows at 2048, dynamic lights off and low-memory mode.
Existing user choices are retained.
Other locations and longer sessions still need coverage. Shadow/effects shader
compilation is verified; their complete in-game behavior is not yet verified.

ZoneRenderer uses CPU sorting and individual draws. Desktop OpenCL/LegacyRenderer,
persistent storage, multi-draw indirect and detailed GPU timer queries are not
implemented by the bridge. Keep the legacy renderer and detailed GPU timing overlay
disabled.
