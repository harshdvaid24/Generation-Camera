# SPEC.md — Generation Camera

## Product
An Android camera app with a tactile **Eras Dial**: pick a decade (1900s–2020s) and shoot photos that look like they were taken with that decade's dominant photographic medium. Inspired by the FUJIFILM instax mini Evo Cinema™ Eras Dial concept, extended back to 1900/1910/1920. All processing on-device, offline, free.

## Functional requirements

| ID | Requirement |
|----|-------------|
| F1 | 13 selectable eras: 1900s, 1910s, …, 2020s, via a rotary dial with detents and era label |
| F2 | Live full-screen camera preview with the selected era look applied in real time |
| F3 | **Degree control 0–10** per era; 0 = subtle, 10 = maximal (intensity model in `ERA_ANALYSIS.md` §0.3); persists per session |
| F4 | **Frame Switch** toggle: era-matched frame overlay shown in preview and composited into the saved photo |
| F5 | Shutter captures a **full-resolution** still rendered through the **same shader pipeline** as the preview (visual parity) |
| F6 | Front/back camera toggle; flash modes off/on/auto (back lens) |
| F7 | In-app gallery of captured photos (newest first) with share/export via system sheet |
| F8 | Saved files land in `Pictures/GenerationCamera/` (MediaStore), JPEG, with era+degree recorded in EXIF UserComment |
| F9 | Dial, degree, and frame state survive rotation and process death |
| F10 | English-only UI, retro-tactile aesthetic (dark hardware look, amber accents) |

## Non-functional requirements

| ID | Requirement |
|----|-------------|
| N1 | Preview ≥ 24 fps on mid-range hardware (e.g. 2022 A-series); graceful on low-end |
| N2 | Capture-to-saved ≤ 4 s at 12 MP on mid-range; UI never blocks |
| N3 | 100 % on-device; **no INTERNET permission** |
| N4 | minSdk 26, target latest stable; OpenGL ES 3.0 required (declared in manifest) |
| N5 | OSS licenses respected; `ATTRIBUTIONS.md` shipped; no claim of replicating Fujifilm's proprietary algorithms anywhere in app or store copy |
| N6 | All era assets (LUTs/overlays/frames) generated reproducibly by `tools/generate_assets.py` |

## Out of scope (v1)
Video recording, RAW capture, iOS, localization, cloud sync, editing existing gallery photos with new eras (re-develop), custom user LUT import. Candidates for v2.

## Acceptance checks
1. Sweep all 13 eras at degrees 0/5/10: preview updates < 1 frame; no stutter, no relink.
2. Capture at each era with frame on: saved JPEG matches preview look; frame crisp at full res.
3. 1900s shows ortho behavior: red object renders near-black, blue sky near-white, sepia tone.
4. 1990s shows scanlines + chroma noise + OSD frame; saved still keeps spatial artifacts, drops temporal flutter.
5. Airplane mode end-to-end run: everything works.
