package a2;

/** Raw audio samples. NOT thread-safe. Must be closed to free native memory. */
public interface AudioBuffer extends AutoCloseable {

    /** Null if closed. */
    double[] getSamples();

    int getSampleRate();

    int getChannelCount();

    long getStartFrame();

    long getFrameCount();

    boolean isClosed();

    @Override
    void close();
}
