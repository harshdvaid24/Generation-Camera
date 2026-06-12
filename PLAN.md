# PLAN.md — Phase 1 Tech Selection & Delivery Plan

## 1.1 Stack decision record

**Decision: Native Kotlin + CameraX + OpenGL ES 3.0 (GLSL) + Jetpack Compose for UI chrome.**

| Option | Assessment |
|--------|------------|
| **Native Kotlin + CameraX + GLES (chosen)** | Real-time per-frame GPU filtering of a live camera preview is the core workload. The zero-copy path — CameraX `Preview` → `SurfaceTexture` → `GL_TEXTURE_EXTERNAL_OES` → shader chain — exists only natively. Full control of EGL, FBOs, `sampler3D` LUTs. One codebase, no bridge. |
| React Native | Camera frames cannot be filtered JS-side at 30 fps; the entire camera/filter engine would have to be a native module / Fabric component anyway, leaving RN as a thin shell around 90 % native code while adding bridge complexity, bundle size, and two build systems. No iOS requirement exists to justify it. **Rejected.** |

Supporting choices:

- **OpenGL ES 3.0** (not 2.0): needed for `sampler3D` (true trilinear 3D LUTs) and GLSL 300 es. ES 3.0 is universal on `minSdk 26` hardware. (Vulkan rejected: enormous boilerplate for zero visual benefit at this workload.)
- **Jetpack Compose** for all UI chrome (dial, slider, gallery); the GL preview is a `GLSurfaceView` wrapped in `AndroidView`.
- **No filter-library dependency** — pipeline built from scratch (decision record in `ERA_ANALYSIS.md` §0.5).
- **MVVM**: a single `CameraViewModel` owns `(eraId, degree, frameOn, lens, flash)` as `StateFlow`; the renderer and UI both observe it.

## 1.2 Base repositories

Survey, licenses, and adopt/reject rationale: see `ERA_ANALYSIS.md` §0.5. Net result: zero runtime third-party filter dependencies; only androidx (CameraX, Compose, Lifecycle) + Kotlin coroutines. Attributions for studied repos in `ATTRIBUTIONS.md`.

## Dependency manifest (all Apache-2.0 / permissive)

- `androidx.camera:camera-camera2 / camera-lifecycle / camera-view` (CameraX)
- `androidx.compose.*` (BOM-pinned), `androidx.activity:activity-compose`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `io.coil-kt:coil-compose` (gallery thumbnails)

## Delivery phases

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 0 | `ERA_ANALYSIS.md` | ✅ |
| 1 | This decision record | ✅ |
| 2 | `ARCHITECTURE.md` | ✅ |
| 3 | GLSL effect engine + 13 generated `.cube` LUTs + overlays/frames (`tools/generate_assets.py`) | ✅ |
| 4 | Compose UI: Eras dial, degree ring, frame switch, shutter, gallery | ✅ |
| 5 | Build instructions, README, attributions, test matrix | ✅ (see "Verification status") |

## Performance plan (targets: ≥24 fps preview on mid-range; no dropped full-res captures)

- Zero-copy camera input (OES texture, no `ImageAnalysis` CPU path for preview).
- Exactly 4 GPU passes/frame; blur runs at **half resolution**; preview FBOs sized to view, not sensor.
- Single über-shader: era changes are **uniform updates only** — no program relink, no LUT stutter (all 13 LUTs ≈ 1.6 MB total, uploaded once at startup as 17³ `sampler3D`s).
- Full-res capture rendered **off the preview hot path** on the GL thread via `queueEvent` against the same context/programs → guaranteed preview/still parity.
- `RENDERMODE_WHEN_DIRTY` + `SurfaceTexture.OnFrameAvailableListener` → render only when a camera frame arrives (no wasted vsyncs).

## Test matrix

| Target | Purpose |
|--------|---------|
| Physical low-end (e.g. Galaxy A1x/A2x class, Mali-G52) | Grain/LUT fill-rate, thermal, real camera timing — **emulators do not reflect GPU filter cost** |
| Physical mid-range (Pixel a-series / equivalent) | Primary target for the 24 fps bar |
| Emulator API 26 (minSdk floor) | Lifecycle, permission, MediaStore paths |
| Emulator API 35 | Latest-API behavior, edge-to-edge |

Manual test passes: era×degree sweep parity between preview and saved JPEG; frame-switch composite at full res; lens flip; process-death restore of dial state.

## Verification status (honest)

This project was authored in a sandboxed environment whose network policy blocks `maven.google.com`/`repo1.maven.org`, so **the Gradle build has not been executed here**. What *was* executed and verified: `tools/generate_assets.py` (all 13 LUTs, overlays, and frames generated and visually spot-checked), `.cube` parser logic cross-checked against generated files, and Gradle wrapper generation. First build on a normal network: open in Android Studio (or `./gradlew assembleDebug`) — see `README.md`.
