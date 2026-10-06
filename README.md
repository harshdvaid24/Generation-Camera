# Generation Camera

An Android camera app with a tactile **Eras Dial**: pick one of 14 stops, every decade from the **1900s to the 2020s** plus **2026** ("Today · as it is"), and shoot photos that look like they were taken with that decade's dominant photographic medium — orthochromatic dry plates, Autochrome, panchromatic 35 mm, Kodachrome-style slides, Super-8, faded 70s prints, VHS, early digicams, and modern computational photography.

Inspired by the FUJIFILM instax mini Evo Cinema™ "Eras Dial" concept, reimagined as a phone app and **extended back to 1900, 1910 and 1920**. This project emulates each era's *look* using standard, published image-processing techniques (3D LUTs, procedural grain, halation, vignette, overlay textures); it makes **no claim** to replicate Fujifilm's proprietary algorithms, and is not affiliated with Fujifilm.

Everything runs **on-device, offline** — the app does not even hold the INTERNET permission.

## Project documents (spec-driven / GSD)

| Doc | Contents |
|-----|----------|
| [`ERA_ANALYSIS.md`](ERA_ANALYSIS.md) | Phase 0: effect-layer decomposition, the per-decade era spec table, the degree intensity model, era-authentic controls, sounds and the 2026 stop (§0.4), the open-source survey |
| [`PLAN.md`](PLAN.md) | Phase 1: stack decision record (native Kotlin vs RN), dependencies, performance plan, test matrix |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Phase 2: MVVM + 4-pass GLES 3.0 pipeline, capture-parity path, threading |
| [`SPEC.md`](SPEC.md) / [`TASKS.md`](TASKS.md) | Requirements and the running task list |
| [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md) | OSS credits |
| [`RELEASING.md`](RELEASING.md) | Release runbook: signing, the GitHub Actions → Google Play pipeline, the Play Console checklist, rollback |

## Building

Requirements: JDK 17 + Android SDK 36 on the command line, or an Android Studio release that supports AGP 8.13 (Narwhal 3 Feature Drop or newer).

```bash
./gradlew assembleDebug          # build
./gradlew test                   # JVM unit tests (LUT parser, intensity model)
./gradlew installDebug           # deploy to a connected device
./gradlew bundleRelease          # release bundle (AAB) for Google Play
```

`bundleRelease` writes `app/build/outputs/bundle/release/app-release.aab`. The bundle is signed only when the `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables are set; signing, versioning and publishing are covered in [`RELEASING.md`](RELEASING.md).

A **physical device** is strongly recommended — era looks depend on real GPU fill-rate and real camera frames; emulators misrepresent both. Min SDK 26, OpenGL ES 3.0 required.

### Regenerating the era assets

All LUTs, dust/light-leak overlays, frame PNGs and shutter sounds are produced deterministically by:

```bash
pip install numpy pillow
python3 tools/generate_assets.py
```

Tweak an era's color recipe in `tools/generate_assets.py` (and its layer amounts in `app/src/main/java/com/generationcamera/engine/Eras.kt`), rerun the script, rebuild.

## The 14 eras and what the dial does

Full research notes per era are in `ERA_ANALYSIS.md` §0.2 (the 2026 stop is in §0.4). In brief:

| Era | Look |
|-----|------|
| **1900s** | Orthochromatic dry plate: sepia B&W where **reds go dark and skies blow out**, heavy oval vignette, pictorialist soft-focus glow, dust and plate scratches |
| **1910s** | Autochrome: pastel, low-saturation color with blotchy starch-grain chroma noise |
| **1920s** | Panchromatic B&W with early-35 mm grain |
| **1930s** | Pale, muted, grainy newsreel B&W |
| **1940s** | High-contrast press B&W with flashbulb punch |
| **1950s** | Warm, saturated slide-film color |
| **1960s** | Super-8 cine: warm cast, strong vignette, **gate weave and projector flicker** in the live preview |
| **1970s** | Faded print: lifted blacks, yellow cast, halation, **light leaks** |
| **1980s** | Vibrant, punchy 35 mm color negative |
| **1990s** | VHS camcorder: heavy down-res, scanlines, chroma bleed, tape flutter, optional camcorder date/time stamp |
| **2000s** | Early digicam: oversharpened, cool flash white balance, shadow noise, optional orange date stamp |
| **2010s** | Early smartphone: mild HDR lift, vibrance |
| **2020s** | Clean, sharp, neutral computational look |
| **2026** | Today, as it is: the faintest polish only — no grain, no defects, full resolution |

**Degree control (0–10):** scales every layer along tuned curves (`ERA_ANALYSIS.md` §0.3). Color identity stays present even at 0 (30 % LUT strength) and ramps linearly; defect layers (grain, dust, leaks, down-res, VHS artifacts) ramp quadratically so low degrees stay clean and high degrees get gloriously trashy. Degree 10 is each era's tuned maximum.

**Frame switch:** turns the shot into an **instant print**. The photo ejects from a printer slot and develops from dark to full image; you can write a short note on the white margin, then save or retake. The saved file is the photo center-cropped square on a white 4:5 card (Instagram-post native) with your handwritten note and a small `decade · camera` credit, for example `1970s · Polaroid SX-70`. With the switch off, the photo is saved frameless with the same credit stamped subtly in a corner.

**Era-authentic controls and sounds:** the dial is a time machine, so the camera only offers controls that existed in the selected decade (`ERA_ANALYSIS.md` §0.4). Exposure is always there; flash and the self-timer arrive in the 1930s, zoom with 1960s Super-8, the on-image date/time stamp exists only in the 1990s and 2000s, and the composition grid comes with the smartphone stops. The selfie lens stays available everywhere as a deliberate usability exception. Each era also plays a period-appropriate synthesized shutter sound, from a plate-camera ka-chunk to a camcorder beep.

**Capture parity:** stills are rendered through the *same GL programs with the same parameters* as the preview, on the same GL context — what you see is what you save. Time-domain layers (gate weave, flicker, tape flutter) are preview-only because a still cannot move; their spatial signatures (grain, scanlines, dust) are kept.

## Status

Every push is unit-tested and built by GitHub Actions ([`android.yml`](.github/workflows/android.yml)). Toolchain: AGP 8.13, Kotlin 2.2, compile and target SDK 36, min SDK 26.

Releases are built, signed and uploaded to Google Play by GitHub Actions, starting with the internal testing track. [`RELEASING.md`](RELEASING.md) covers the pipeline, the one-time Play Console setup and the device smoke test to run before each rollout.

## Privacy

Generation Camera works offline and holds no INTERNET permission. Photos are saved only to `Pictures/GenerationCamera` on your device; nothing is collected or sent anywhere. See the [privacy policy](https://harshdvaid24.github.io/Generation-Camera/privacy.html).

## License

No license file yet — the repository owner should pick one. All third-party study credits are in `ATTRIBUTIONS.md`; the app has no runtime third-party filter dependencies (androidx + Coil only).
