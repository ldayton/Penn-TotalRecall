# Overview

This app renders and annotates audio by flowing a few simple ideas in one direction. Think of it as:

1) Audio timeline (what's playing) → 2) Viewport (what size/zoom the user wants) → 3) Projection (which slice of time fits) → 4) Waveform render (turn that slice into an image) → 5) Painter (draw the image + overlays).

Key ideas, in plain terms:

- Audio snapshot: A read‑only “photo” of playback (state, total frames, playhead). It never contains UI info. Everyone else reads this; only the audio session writes it.
- Viewport UI state: The user’s visual choices (zoom as frames per pixel, canvas size). No audio here.
- Projection: Given snapshot + UI state, compute the exact time window (start/end frames) to display. It’s pure math and deterministic.
- Waveform window: Convert that frame‑window into seconds for the renderer and ask for an image. This is the only place seconds appear.
- Render spec: A single “what to draw” package for the painter: mode (loading/error/content), optional message, the image future, and a generation id to avoid stale repaints.
- Painter: Dumb on purpose. It draws the image full‑bounds and overlays the playhead at the center.

Benefits of this shape:

- Frames‑first logic makes zoom and window math precise and sample‑rate agnostic.
- One‑way flow keeps responsibilities crisp and prevents feedback loops.
- A single render spec keeps the painter simple and testable.

# Core Concepts and Proposed Renames

| Concept (what it represents) | Current Name | Kind | Package | Proposed Rename | Notes |
|---|---|---|---|---|---|
| Audio timeline snapshot (authoritative playback state) | `AudioSessionDataSource.AudioSessionSnapshot` (method: `snapshot()`) | record | `core.audio.session` | `AudioSnapshot` (method: `snapshot()`) | Minimal: `state, totalFrames, playheadFrame, errorMessage`. Frames-first; no UI concerns. |
| Audio session (transport + lifetime owner) | `AudioSession` | class | `core.audio.session` | Keep | Applies commands; now maintains explicit `playheadFrame`. |
| Audio session service (manages session, exposes snapshot) | `AudioSessionManager` | class | `core.audio.session` | Keep | Produces `snapshot()`; owns engine lifecycle. |
| Audio read boundary for other layers | `AudioSessionDataSource` | interface | `core.audio.session` | Keep | Prefer `snapshot()` over granular getters in new code. |
| Viewport UI state (user-controlled zoom/size) | `ViewportProjector.ViewportUiState` | record | `core.viewport` | Keep (field name: `framesPerPixel`) | Stores `framesPerPixel, widthPx, heightPx`; sample-rate agnostic. |
| Projection (timeline → visible window) | `ViewportProjector.Projection` | record | `core.viewport` | `TimelineWindow` | Deterministic: `startFrame, endFrame, generation, (optional error)`. |
| Waveform renderer spec (window for renderer) | `WaveformViewportSpec` | record | `core.waveform` | `WaveformWindow` | Seconds-based; produced only at renderer boundary. |
| Viewport orchestrator (compose render spec) | `ViewportSessionManager` | class | `core.viewport` | `ViewportService` | Holds minimal UI state (zoom); reads `AudioSnapshot`, calls projector, requests image. |
| Painter render instruction | `ViewportPaintingDataSource.ViewportRenderSpec` | record | `core.viewport` | `RenderSpec` | Contains `mode, errorMessage, imageFuture, generation`. Painter is dumb. |
| Painter mode enum | `PaintMode` | enum | `core.viewport` | `RenderMode` | Values: `EMPTY, LOADING, ERROR, CONTENT` (rename from `RENDER` to `CONTENT`). |
| Render-spec provider to painter | `ViewportPaintingDataSource` | interface | `core.viewport` | `RenderSpecSource` | Single method: `getRenderSpec(bounds)`. |
| Painter/renderer | `ViewportPainter` | class | `ui.viewport` | `ViewportRenderer` | Non-blocking draw cycle, EDT-safe callbacks, generation guard on image completion. |
| Render surface (where it draws) | `ViewportCanvas` (implements `WaveformViewport`) | class | `ui.viewport` | `RenderSurface` (optional) | Minimal API: `getViewportBounds(), getPaintGraphics(), repaint(), isVisible()`. |
| Projector (pure mapping) | `ViewportProjector` | interface | `core.viewport` | Keep | Methods: `project(snapshot, ui)` → `TimelineWindow`; `toWaveformWindow(window, ui, sampleRate)`. |
| Zoom scale (frames per pixel) | `framesPerPixel` | double (field) | `core.viewport` (service state) | `framesPerPixel` (keep) or `timelineScale` | Frames-first naming clarifies scaling math: frames = px × fpp. |
| Waveform lifecycle manager | `WaveformManager` | class | `core.waveform` | Keep | Creates/shuts down `Waveform` on audio load/close. |
| Audio handle/path for renderer | `getCurrentAudioHandle`, `getCurrentAudioFilePath` | methods | `core.audio.session.AudioSessionDataSource` | Keep | Required by `WaveformManager`. |

## One-Way Flow (frames-first)

`AudioSessionManager.snapshot()` → `ViewportService` (with `ViewportUiState`) → `ViewportProjector.project()` → `TimelineWindow` → `ViewportProjector.toWaveformWindow()` → `Waveform.render()` → `RenderSpec` → Painter.

## Painter Layer Details

- RenderSpec: single input to the renderer (mode, optional error text, image future, generation).
- RenderMode: `EMPTY, LOADING, ERROR, CONTENT` (clear + draw text in non-content modes).
- ViewportRenderer:
  - Gets a RenderSpec from the source each paint tick.
  - Draws full-bounds image when available; otherwise clears and schedules repaint on completion.
  - Always draws the playhead at 50% width as an overlay.
  - Uses `generation` to ignore out-of-order image completions; EDT-safe via `SwingUtilities.invokeLater`.

## Waveform (Core) vs UI (No `ui/waveform` package)

There is intentionally no `ui/waveform` package. The waveform rendering engine is entirely in `core.waveform` and the UI integrates it via `ui.viewport`:

- Core responsibilities (in `core.waveform`):
  - `Waveform`: orchestrates rendering and manages a render thread pool.
  - `WaveformRenderer`: composes 200px segments, performs prefetching and image compositing.
  - `WaveformSegmentCache`: holds segment futures keyed by start time, resolution, and height.
  - `WaveformWindow` (current class name: `WaveformViewportSpec`): seconds-based window for the renderer.
- UI responsibilities (in `ui.viewport`):
  - `ViewportCanvas` (optional rename: `RenderSurface`): a thin Swing surface; no timeline logic.
  - `ViewportRenderer` (current: `ViewportPainter`): consumes `RenderSpec` and draws.
  - No UI component owns waveform internals — the UI only requests an image from `Waveform` using `WaveformWindow`.

This split keeps rendering decisions (windowing, scale, caching) driven by the projector + service on the core side, while the UI strictly draws.

## Rename Plan (Short Names First)

Start with the short, high-visibility names to improve readability without changing behavior.

- Phase 1 — UI surface and renderer
  - `ViewportPainter` → `ViewportRenderer`
  - `PaintMode` → `RenderMode` (values: `EMPTY, LOADING, ERROR, CONTENT`)
  - `ViewportPaintingDataSource` → `RenderSpecSource`
  - `ViewportRenderSpec` → `RenderSpec`
  - Optional: `ViewportCanvas` → `ViewportSurface` (keep if you prefer “Canvas”)

- Phase 2 — Waveform spec boundary (renderer input)
  - `WaveformViewportSpec` → `WaveformWindow`

- Phase 3 — Orchestration and projection
  - `ViewportSessionManager` → `ViewportService` (compose `RenderSpec` only)
  - `ViewportProjector.Projection` → `TimelineWindow`
  - `ViewportProjector.toWaveformViewport(...)` → `toWaveformWindow(...)`

- Phase 4 — Audio snapshot naming polish (optional)
  - `AudioSessionDataSource.AudioSessionSnapshot` → `AudioSnapshot`
  - Method `snapshot()` (keep current signature)

Notes

- No behavior changes are required for any of these renames.
- Apply in phases to keep diffs small and easy to review.
- Favor “Renderer/Window/Service/Spec/Mode” for role clarity across the codebase.
