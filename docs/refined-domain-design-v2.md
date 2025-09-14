# Refined Domain Design (v2)

This document refines the domain design with precise names, a frames-first model, and a one-way flow. It focuses on minimal, high-impact renames and clear boundaries to keep the code easy to read and evolve.

## Overview

One-way, frames-first flow:

1) PlaybackSnapshot (what’s playing) → 2) ViewportUiState (how we look) → 3) TimelineWindow (what we see) → 4) WaveformWindow (renderer input, seconds) → 5) RenderSpec (what to draw) → Renderer

Benefits
- Frames-first math: precise, sample-rate agnostic windowing/zoom.
- One read model: PlaybackSnapshot is the single audio truth.
- Thin coordination: a small service composes a RenderSpec; UI only draws.
- Clear units: frames in viewport/timeline; seconds only at renderer boundary.

---

## Domains and Core Types

### Playback (Audio Control)
Responsible for transport and the read model.

| Concept                       | Name                     | Kind      | Notes                                                                               |
| ----------------------------- | ------------------------ | --------- | ----------------------------------------------------------------------------------- |
| Snapshot of playback          | `PlaybackSnapshot`       | record    | state, totalFrames, playheadFrame, errorMessage, sampleRate (if needed at boundary) |
| Session (transport owner)     | `AudioSession`           | class     | Applies commands; owns explicit `playheadFrame` updates                             |
| Service (lifecycle, snapshot) | `AudioSessionManager`    | class     | Exposes `snapshot()` for downstream consumers                                       |
| Read boundary                 | `AudioSessionDataSource` | interface | Prefer `snapshot()` over granular time getters                                      |

Recommended practice
- Playback registers a low-level PlaybackListener and applies `UpdatePosition` to keep `playheadFrame` live.
- Clear old `errorMessage` when transitioning to READY.

### Viewport (Window into Timeline)
Responsible for translating UI intent (zoom/size) into a timeline window.

| Concept         | Name                       | Kind      | Notes                                                         |
| --------------- | -------------------------- | --------- | ------------------------------------------------------------- |
| UI state        | `ViewportUiState`          | record    | framesPerPixel, widthPx, heightPx                             |
| Projector       | `ViewportProjector`        | interface | `project(PlaybackSnapshot, ViewportUiState) → TimelineWindow` |
| Projector impl  | `DefaultViewportProjector` | class     | Centered view (playhead at 50%)                               |
| Window (frames) | `TimelineWindow`           | record    | startFrame, endFrame, generation                              |

Viewport rules
- framesPerPixel is the zoom; it never contains seconds.
- Follow mode (optional): Viewport manager sets centerFrame := snapshot.playheadFrame.

### Waveform (Renderer Boundary)
Responsible for generating the waveform image from a seconds window.

| Concept             | Name                                          | Kind   | Notes                                                        |
| ------------------- | --------------------------------------------- | ------ | ------------------------------------------------------------ |
| Renderer façade     | `Waveform`                                    | class  | Orchestrates render and caching                              |
| Seconds-based input | `WaveformWindow` (was `WaveformViewportSpec`) | record | startSeconds, endSeconds, widthPx, heightPx, pixelsPerSecond |
| Renderer            | `WaveformRenderer`                            | class  | Segment compositor (200 px)                                  |
| Cache               | `WaveformSegmentCache`                        | class  | Keys based on seconds/resolution/height                      |

Boundary rule
- Convert frames → seconds only here (TimelineWindow + sampleRate → WaveformWindow).

### Display (Composition → UI)
A thin service composes a render instruction; the UI strictly draws it.

| Concept                      | Name                                             | Kind      | Notes                                                  |
| ---------------------------- | ------------------------------------------------ | --------- | ------------------------------------------------------ |
| Render instruction           | `RenderSpec`                                     | record    | mode, errorMessage?, image: Future<Image>, generation  |
| Mode enum                    | `RenderMode`                                     | enum      | `EMPTY, LOADING, ERROR, CONTENT`                       |
| Producer                     | `RenderSpecSource`                               | interface | `getRenderSpec(bounds)` only                           |
| Service (orchestration-lite) | `ViewportService` (was `ViewportSessionManager`) | class     | Snapshot + UiState + Projector + Waveform → RenderSpec |

Concurrency rules (UI)
- Renderer never blocks for images. On completion, schedule repaint on EDT via `SwingUtilities.invokeLater`.
- Guard completions with `generation` to ignore stale results.

### UI (Swing)

| Concept  | Name                                          | Kind       | Notes                                                       |
| -------- | --------------------------------------------- | ---------- | ----------------------------------------------------------- |
| Surface  | `ViewportCanvas` (optional `ViewportSurface`) | JComponent | Thin drawing surface; no timeline logic                     |
| Renderer | `ViewportRenderer` (was `ViewportPainter`)    | class      | Consumes `RenderSpec`, draws full-bounds + playhead overlay |

---

## One-Way Flow (Detailed)

```
1. Playback
   AudioSessionManager.snapshot() → PlaybackSnapshot

2. Viewport (frames)
   ViewportUiState (framesPerPixel, widthPx, heightPx)
   ViewportProjector.project(PlaybackSnapshot, ViewportUiState) → TimelineWindow (startFrame, endFrame, generation)

3. Renderer boundary (seconds)
   toWaveformWindow(TimelineWindow, ViewportUiState, sampleRate) → WaveformWindow
   Waveform.render(WaveformWindow) → Future<Image>

4. Compose render instruction
   ViewportService.getRenderSpec(bounds) → RenderSpec
   - mode: RenderMode (EMPTY/LOADING/ERROR/CONTENT)
   - errorMessage: Optional
   - image: Future<Image>
   - generation: long

5. Paint
   ViewportRenderer renders RenderSpec (non-blocking, EDT-safe, generation-guarded)
```

---

## Minimal Rename Plan (High Impact, Low Risk)

Phase 1 — UI (short names)
- `ViewportPainter` → `ViewportRenderer`
- `PaintMode` → `RenderMode` (values: `EMPTY, LOADING, ERROR, CONTENT`)
- `ViewportPaintingDataSource` → `RenderSpecSource`
- `ViewportRenderSpec` → `RenderSpec`
- Optional: `ViewportCanvas` → `ViewportSurface`

Phase 2 — Renderer boundary
- `WaveformViewportSpec` → `WaveformWindow`

Phase 3 — Orchestration + projection
- `ViewportSessionManager` → `ViewportService`
- `ViewportProjector.Projection` → `TimelineWindow`
- `toWaveformViewport(...)` → `toWaveformWindow(...)`

Phase 4 — Playback naming polish (optional)
- `AudioSessionDataSource.AudioSessionSnapshot` → `PlaybackSnapshot`
- Method `snapshot()` retained (single read model)

Notes
- No behavior changes are required in these phases;
- Apply changes in phases to keep diffs small and reviewable;
- Favor the vocabulary Renderer/Window/Service/Spec/Mode for clear roles.

---

## Unit Boundaries and Invariants

- Frames only until the renderer boundary; seconds only inside Waveform.
- `framesPerPixel > 0`, `widthPx > 0`, `heightPx > 0` (validate at source).
- `TimelineWindow.startFrame >= 0`, `endFrame >= startFrame`.
- `generation` changes whenever Snapshot/UiState/Window changes (deterministic hash).

---

## Why this works

- Frames-first windowing produces correct, sample-rate-agnostic zooming.
- A single `PlaybackSnapshot` prevents fragmented reads and races.
- `TimelineWindow` + `WaveformWindow` remove unit confusion (frames vs seconds).
- `RenderSpec` isolates the UI; the painter becomes dumb and safe.

