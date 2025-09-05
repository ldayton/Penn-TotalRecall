package w2;

/**
 * Data source for waveform painting operations. Provides temporal information needed by the painter
 * to render waveforms.
 */
public interface WaveformPaintingDataSource {

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
