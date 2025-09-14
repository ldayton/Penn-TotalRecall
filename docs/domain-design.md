# Refined Domain Design

## Core Insight
The **Viewport** is the central concept that bridges logical timeline calculations with visual display. It's not just a UI component - it's the fundamental abstraction for "looking at audio."

## Domain Model

### Three Clear Domains

```
PLAYBACK (Audio Control) → VIEWPORT (Window into Audio) → WAVEFORM (Visual Representation)
```

## 1. Playback Domain
*Controls audio playback and provides current state*

| Class                            | Purpose                                | Key Data                                              |
| -------------------------------- | -------------------------------------- | ----------------------------------------------------- |
| `core.playback.PlaybackEngine`   | Low-level audio operations             |                                                       |
| `core.playback.PlaybackSession`  | Active playback session                | File, position, state                                 |
| `core.playback.PlaybackSnapshot` | Immutable snapshot of current playback | `playheadFrame`, `totalFrames`, `sampleRate`, `state` |

**Single Responsibility**: Tell us what's playing and where we are in the audio.

## 2. Viewport Domain
*The window through which we view the audio timeline*

| Class                             | Purpose                   | Key Data                                               |
| --------------------------------- | ------------------------- | ------------------------------------------------------ |
| `core.viewport.Viewport`          | The viewing window        | `centerFrame`, `framesPerPixel`, `widthPx`, `heightPx` |
| `core.viewport.ViewportManager`   | Manages viewport state    | Handles zoom, pan, follows playback                    |
| `core.viewport.TimelineWindow`    | What the viewport sees    | `startFrame`, `endFrame`, `visibleDuration`            |
| `core.viewport.ViewportProjector` | Calculates what's visible | `(Viewport, PlaybackSnapshot) → TimelineWindow`        |

**Single Responsibility**: Determine what portion of the audio timeline is visible.

### The Viewport Object
```java
record Viewport(
    long centerFrame,        // Where we're looking
    double framesPerPixel,   // How zoomed in we are
    int widthPx,            // Physical width
    int heightPx            // Physical height
) {
    // Calculated properties
    long visibleFrames() { return (long)(widthPx * framesPerPixel); }
    long startFrame() { return centerFrame - visibleFrames() / 2; }
    long endFrame() { return centerFrame + visibleFrames() / 2; }
}
```

## 3. Waveform Domain
*Generates visual representations of audio*

| Class                             | Purpose                  | Key Transformation                                   |
| --------------------------------- | ------------------------ | ---------------------------------------------------- |
| `core.waveform.WaveformRequest`   | What to render           | `TimelineWindow` + dimensions → request              |
| `core.waveform.WaveformImage`     | Generated waveform image | Contains `BufferedImage` + metadata                  |
| `core.waveform.WaveformGenerator` | Creates waveform images  | `WaveformRequest → CompletableFuture<WaveformImage>` |
| `core.waveform.WaveformCache`     | Caches rendered segments | `CacheKey → CompletableFuture<Image>`                |
| `core.waveform.WaveformSegment`   | 200px cached piece       | Fixed-width for efficient caching                    |

**Single Responsibility**: Generate visual waveforms for requested time ranges.

## 4. Display Coordination
*Thin layer that combines everything for rendering*

| Class                         | Purpose                    | Key Data                                         |
| ----------------------------- | -------------------------- | ------------------------------------------------ |
| `core.display.RenderState`    | What to display            | `EMPTY`, `LOADING`, `ERROR`, `READY`             |
| `core.display.RenderSpec`     | Complete rendering package | `RenderState`, `WaveformImage?`, `errorMessage?` |
| `core.display.DisplayService` | Coordinates rendering      | `(Viewport, PlaybackSnapshot) → RenderSpec`      |

**Single Responsibility**: Package everything the UI needs to paint.

## 5. UI Layer
*Swing-specific components*

| Class                          | Purpose                         |
| ------------------------------ | ------------------------------- |
| `ui.viewport.ViewportPanel`    | JComponent that owns a Viewport |
| `ui.viewport.ViewportPainter`  | Paints RenderSpec to Graphics2D |
| `ui.playback.PlaybackControls` | Play/pause/seek UI              |

## Data Flow

```
1. User Action
   Zoom/Pan → ViewportPanel → ViewportManager → Viewport (updated)

2. Calculate View
   Viewport + PlaybackSnapshot → ViewportProjector → TimelineWindow

3. Generate Waveform
   TimelineWindow → WaveformRequest → WaveformGenerator → WaveformImage

4. Package for Display
   DisplayService combines:
   - Viewport (for overlay positioning)
   - WaveformImage (the visual)
   - PlaybackSnapshot (for playhead)
   → RenderSpec

5. Paint
   RenderSpec → ViewportPainter → Graphics2D
```

## Key Design Decisions

### 1. Viewport is Central
- It's a real object with clear properties
- It bridges logical (frames) and visual (pixels)
- It's the source of truth for "what we're looking at"

### 2. Keep Domain Terminology
- "Waveform" not "ImageGenerator" - it's what audio engineers call it
- "Playback" not "Audio" - it's specifically about playing audio
- "Viewport" not "View" - it's a viewport into the timeline

### 3. Clear Boundaries
- Playback knows nothing about display
- Viewport knows about frames and pixels but not about rendering
- Waveform generates images but doesn't know about viewport logic
- Display coordination is thin - just packaging

### 4. Natural Transformations
Each domain has clear inputs and outputs:
- Playback: `File → PlaybackSnapshot`
- Viewport: `(zoom, pan, follow) → Viewport → TimelineWindow`
- Waveform: `TimelineWindow → WaveformImage`
- Display: `(all above) → RenderSpec`

### 5. No Orchestration Layer
Instead of heavy orchestration, domains connect through simple data:
- `PlaybackSnapshot` flows from Playback to Viewport
- `TimelineWindow` flows from Viewport to Waveform
- `WaveformImage` flows from Waveform to Display
- No complex coordination needed

## What This Design Fixes

### From Previous Critique:
1. ✅ **Viewport is now real** - It's the central concept
2. ✅ **Domain boundaries are natural** - Based on actual responsibilities
3. ✅ **Kept domain terminology** - Waveform, Playback, Viewport
4. ✅ **Complete transformations** - Every class shows its data flow
5. ✅ **No artificial separation** - Domains align with natural concepts
6. ✅ **Consistent abstraction** - All at the same conceptual level
7. ✅ **Thin coordination** - DisplayService just packages, doesn't orchestrate

### Conceptual Clarity:
- **Timeline is logical** - Lives in Viewport domain as frame calculations
- **Display is visual** - Lives in Waveform domain as image generation
- **Viewport bridges them** - Has both logical (frames) and visual (pixels) properties
