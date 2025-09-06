package waveform.signal;

/** Frequency range for bandpass filtering using normalized values (0.0 to 0.5). */
record FrequencyRange(double minFrequency, double maxFrequency) {

    /** Validates frequency range on construction. */
    public FrequencyRange {
        if (minFrequency < 0 || maxFrequency > 0.5 || minFrequency >= maxFrequency) {
            throw new IllegalArgumentException(
                    "Invalid frequency range: " + minFrequency + " to " + maxFrequency);
        }
    }
}
