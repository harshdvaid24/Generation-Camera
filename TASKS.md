# TASKS.md — GSD task list

## Phase 0 — Analysis
- [x] Layer decomposition of "era look" (10 layers, pipeline placement) → `ERA_ANALYSIS.md` §0.1
- [x] Era Spec Table, 13 decades incl. 1900/1910/1920, research-grounded → §0.2
- [x] Degree (0–10) intensity model with per-layer curves → §0.3
- [x] OSS survey (gpuimage, gpuimage-plus, Awesome-Filters, camerax-gpuimage, retroboy) + adopt/reject → §0.5

## Phase 1 — Tech selection
- [x] Native Kotlin vs RN decision record → `PLAN.md`
- [x] GLES 3.0 / no-filter-library decision; dependency manifest

## Phase 2 — Architecture
- [x] `ARCHITECTURE.md`: MVVM, EraEngine, 4-pass GL pipeline, parity capture path, asset layout, threading

## Phase 3 — Effect engine
- [x] `tools/generate_assets.py`: 13 `.cube` LUTs (color science per era), dust ×3, leaks ×2, frames ×13 — **executed, assets committed**
- [x] GLSL: `fullscreen.vert`, `oes_copy.frag` (weave/down-res), `blur.frag` (separable), `era.frag` (LUT+grain+vignette+halation+softfocus+dust+leak+scanlines+flutter+flicker+unsharp), `frame.frag`
- [x] `CubeLutParser.kt`, `GlUtils.kt` (program/FBO/3D-texture helpers)
- [x] `Eras.kt` (13 configs), `EraEngine.kt` (degree curves → `EffectParams`)
- [x] `EraRenderer.kt` 4-pass renderer, dirty-render on camera frame
- [x] `StillProcessor.kt` full-res capture through same context/programs + frame composite + MediaStore save

## Phase 4 — UI/UX
- [x] `CameraScreen` (Compose): GLSurfaceView host, shutter, flash, lens flip, gallery button
- [x] `EraDial` rotary composable (drag-to-rotate, detents, haptics, era label)
- [x] `DegreeRing` slider 0–10 + `FrameSwitch` toggle
- [x] `GalleryScreen`: MediaStore grid (Coil), tap-to-view, share
- [x] Retro dark theme, English-only strings

## Phase 5 — Build & deliver
- [x] Gradle (Kotlin DSL, version catalog), wrapper, manifest (no INTERNET; GLES3 required)
- [x] Unit tests: CubeLutParser, EraEngine degree curves
- [x] `README.md` (per-era look guide + degree behavior + build steps), `ATTRIBUTIONS.md`
- [ ] **Compile + run on hardware** — blocked in authoring sandbox (maven.google.com unreachable); first action on a dev machine: `./gradlew assembleDebug`, then run the manual test matrix in `PLAN.md`
- [ ] Tune era parameters on real captures (expected: 1–2 rounds of LUT regeneration via the script)
- [ ] Low-end device perf pass (grain/LUT fill rate), adjust blur FBO scale if needed

## Backlog (v2)
- [ ] Filtered video capture with live gate-weave/flutter (gpuimage-plus-style encoder or MediaCodec surface input)
- [ ] Re-develop gallery photos into other eras
- [ ] Dynamic VHS timestamp / 2000s date stamp using capture time
- [ ] Custom `.cube` import
