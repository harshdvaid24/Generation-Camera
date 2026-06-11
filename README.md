# Generation Camera

An Android camera app with a tactile **Eras Dial**: pick a decade from the **1900s to the 2020s** and shoot photos that look like they were taken with that decade's dominant photographic medium — orthochromatic dry plates, Autochrome, panchromatic 35 mm, Kodachrome-style slides, Super-8, faded 70s prints, VHS, early digicams, and modern computational photography.

Inspired by the FUJIFILM instax mini Evo Cinema™ "Eras Dial" concept, reimagined as a phone app and **extended back to 1900, 1910 and 1920**. This project emulates each era's *look* using standard, published image-processing techniques (3D LUTs, procedural grain, halation, vignette, overlay textures); it makes **no claim** to replicate Fujifilm's proprietary algorithms, and is not affiliated with Fujifilm.

Everything runs **on-device, offline** — the app does not even hold the INTERNET permission.

## Project documents (spec-driven / GSD)

| Doc | Contents |
|-----|----------|
| [`ERA_ANALYSIS.md`](ERA_ANALYSIS.md) | Phase 0: effect-layer decomposition, the full 13-era spec table, the degree intensity model, the open-source survey |
| [`PLAN.md`](PLAN.md) | Phase 1: stack decision record (native Kotlin vs RN), dependencies, performance plan, test matrix |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Phase 2: MVVM + 4-pass GLES 3.0 pipeline, capture-parity path, threading |
| [`SPEC.md`](SPEC.md) / [`TASKS.md`](TASKS.md) | Requirements and the running task list |
| [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md) | OSS credits |

## Building

Requirements: Android Studio (Koala+) or a JDK 17 + Android SDK 35 command line.

```bash
./gradlew assembleDebug          # build
./gradlew test                   # JVM unit tests (LUT parser, intensity model)
./gradlew installDebug           # deploy to a connected device
```

A **physical device** is strongly recommended — era looks depend on real GPU fill-rate and real camera frames; emulators misrepresent both. Min SDK 26, OpenGL ES 3.0 required.

### Regenerating the era assets

All LUTs, dust/light-leak overlays and frame PNGs are produced deterministically by:

```bash
pip install numpy pillow
python3 tools/generate_assets.py
```

Tweak an era's color recipe in `tools/generate_assets.py` (and its layer amounts in `app/src/main/java/com/generationcamera/engine/Eras.kt`), rerun the script, rebuild.

## The 13 eras and what the dial does

Full research notes per era are in `ERA_ANALYSIS.md` §0.2. In brief:

| Era | Look |
|-----|------|
| **1900s** | Orthochromatic dry plate: sepia B&W where **reds go dark and skies blow out**, heavy oval vignette, pictorialist soft-focus glow, dust and plate scratches |
| **1910s** | Autochrome: pastel, low-saturation color with blotchy starch-grain chroma noise |
| **1920s** | Panchromatic B&W with early-35 mm grain and a film-gate frame |
| **1930s** | Pale, muted, grainy newsreel B&W |
| **1940s** | High-contrast press B&W with flashbulb punch |
| **1950s** | Warm, saturated slide-film color |
| **1960s** | Super-8 cine: warm cast, strong vignette, **gate weave and projector flicker** in the live preview |
| **1970s** | Faded print: lifted blacks, yellow cast, halation, **light leaks** |
| **1980s** | Vibrant, punchy 35 mm color negative |
| **1990s** | VHS camcorder: heavy down-res, scanlines, chroma bleed, tape flutter, REC/timestamp frame |
| **2000s** | Early digicam: oversharpened, cool flash white balance, shadow noise, orange date stamp |
| **2010s** | Early smartphone: mild HDR lift, vibrance |
| **2020s** | Clean, sharp, neutral computational look |

**Degree control (0–10):** scales every layer along tuned curves (`ERA_ANALYSIS.md` §0.3). Color identity stays present even at 0 (30 % LUT strength) and ramps linearly; defect layers (grain, dust, leaks, down-res, VHS artifacts) ramp quadratically so low degrees stay clean and high degrees get gloriously trashy. Degree 10 is each era's tuned maximum.

**Frame switch:** overlays an era-matched frame (plate border, film gate with sprockets, deckled print border, Super-8 gate, VHS OSD, date stamp…) on the live preview and composites it into the saved photo at full resolution.

**Capture parity:** stills are rendered through the *same GL programs with the same parameters* as the preview, on the same GL context — what you see is what you save. Time-domain layers (gate weave, flicker, tape flutter) are preview-only because a still cannot move; their spatial signatures (grain, scanlines, dust) are kept.

## Status

Authored end-to-end (docs → assets → engine → UI → tests) in a sandboxed environment where `maven.google.com` is unreachable, so the Gradle build has **not yet been compiled here**; asset generation and the LUT color science were executed and verified. Expect a normal first-build shakeout (see `TASKS.md` Phase 5) plus an on-device tuning pass of the era parameters.

## License

No license file yet — the repository owner should pick one. All third-party study credits are in `ATTRIBUTIONS.md`; the app has no runtime third-party filter dependencies (androidx + Coil only).
