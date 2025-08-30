package ui.waveform;

import java.util.ArrayList;
import java.util.List;

/** Test-only probe to collect paint timestamps for WaveformDisplay. */
public final class WaveformDisplayTestProbe {
    private static final Object LOCK = new Object();
    private static final List<Long> TIMES = new ArrayList<>();

    public static void recordPaint(long nanos) {
        synchronized (LOCK) {
            TIMES.add(nanos);
        }
    }

    public static List<Long> getTimesCopy() {
        synchronized (LOCK) {
            return new ArrayList<>(TIMES);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            TIMES.clear();
        }
    }
}
