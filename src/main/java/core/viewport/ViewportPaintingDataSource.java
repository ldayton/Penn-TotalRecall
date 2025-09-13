package core.viewport;

import core.waveform.TimeRange;
import core.waveform.Waveform;

/**
 * Data source for viewport painting operations. Provides temporal information needed by the painter
 * to render the viewport.
 */
public interface ViewportPaintingDataSource {

    /**
     * Get the current time range to display.
     *
     * @return Time range, or null if no audio loaded
     */
    TimeRange getTimeRange();

    /**
     * Get the current zoom level.
     *
     * @return Pixels per second
     */
    int getPixelsPerSecond();

    /**
     * Get the current playback position.
     *
     * @return Playback position in seconds
     */
    double getPlaybackPositionSeconds();

    /**
     * Get the current waveform to render.
     *
     * @return Waveform, or null if not loaded
     */
    Waveform getWaveform();

    /**
     * Check if currently playing.
     *
     * @return true if playing
     */
    boolean isPlaying();
}
