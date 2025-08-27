package audio.signal;

/**
 * Frequency range specification for bandpass filtering.
 * 
 * Uses normalized frequency values (0.0 to 0.5) as required by digital signal processing.
 */
public class FrequencyRange {
    public final double minFrequency;
    public final double maxFrequency;
    
    public FrequencyRange(double minFrequency, double maxFrequency) {
        if (minFrequency < 0.0 || minFrequency > 0.5) {
            throw new IllegalArgumentException("Min frequency must be between 0.0 and 0.5: " + minFrequency);
        }
        if (maxFrequency < 0.0 || maxFrequency > 0.5) {
            throw new IllegalArgumentException("Max frequency must be between 0.0 and 0.5: " + maxFrequency);
        }
        if (minFrequency >= maxFrequency) {
            throw new IllegalArgumentException("Min frequency must be less than max frequency");
        }
        
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
    }
    
    @Override
    public String toString() {
        return String.format("FrequencyRange[%.4f - %.4f]", minFrequency, maxFrequency);
    }
}