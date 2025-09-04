package waveform;

import java.util.ArrayDeque;
import java.util.Deque;

/** Signal processing operations for audio enhancement. */
final class SignalEnhancer {

    /** Applies envelope smoothing to reduce noise in audio signal using sliding window maximum. */
    public double[] envelopeSmooth(double[] samples, int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("Window size must be >= 1: " + windowSize);
        }

        // Need original values for window calculations - prevents feedback from modified values
        double[] originalSamples = new double[samples.length];
        System.arraycopy(samples, 0, originalSamples, 0, originalSamples.length);

        // Use sliding window maximum with monotonic deque for O(n) complexity
        Deque<Integer> maxDeque = new ArrayDeque<>();

        for (int i = 0; i < samples.length; i++) {
            int windowStart = Math.max(0, i - windowSize);
            int windowEnd = Math.min(samples.length, i + windowSize);

            // Remove elements outside current window from front
            while (!maxDeque.isEmpty() && maxDeque.peekFirst() < windowStart) {
                maxDeque.removeFirst();
            }

            // Add elements to the window from the right
            // For i=0, add entire window [windowStart, windowEnd)
            // For i>0, only add new elements that entered the window
            int addStart =
                    (i == 0)
                            ? windowStart
                            : Math.max(windowStart, Math.min(samples.length, (i - 1) + windowSize));
            int addEnd = windowEnd;

            for (int j = addStart; j < addEnd; j++) {
                double absValue = Math.abs(originalSamples[j]);

                // Remove smaller elements from back to maintain monotonic decreasing order
                while (!maxDeque.isEmpty()
                        && Math.abs(originalSamples[maxDeque.peekLast()]) <= absValue) {
                    maxDeque.removeLast();
                }
                maxDeque.addLast(j);
            }

            // Front of deque has the index of maximum element in current window
            samples[i] = maxDeque.isEmpty() ? 0.0 : Math.abs(originalSamples[maxDeque.peekFirst()]);
        }

        // logger.debug(
        //         "Applied envelope smoothing (window={}) to {} samples", windowSize,
        // samples.length);
        return samples;
    }
}
