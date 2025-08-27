package waveform;

/**
 * Frequency range specification for bandpass filtering.
 *
 * <p>Uses normalized frequency values (0.0 to 0.5) as required by digital signal processing.
 */
public class FrequencyRange {
    public final double minFrequency;
    public final double maxFrequency;

    public FrequencyRange(double minFrequency, double maxFrequency) {
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
    }

    @Override
    public String toString() {
        return String.format("FrequencyRange[%.4f - %.4f]", minFrequency, maxFrequency);
    }
}
