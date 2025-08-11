package components.waveform;

/**
 * A <code>Thread</code> that is tied to a single audio file.
 *
 * <p>Instances of this class are guaranteed that between the time they are created and destroyed
 * {@link control.CurAudio#audioOpen()} will return <code>true</code> and the audio file will not
 * switch.
 *
 * <p>Since each instance of this class is terminated at each file switch, methods are provided for
 * noting whether the <code>Thread</code> dies in a timely fashion. Those methods are provided to
 * provide clear evidence that threads are not being unintentionally proliferated.
 */
public abstract class Buffer extends Thread {

    /**
     * Tries for period of time to terminate the thread.
     *
     * @param millis The minimum number of milliseconds to try terminating the thread for
     * @return Whether or not the thread was successfully terminated in the provided period
     * @throws InterruptedException If an attempt at sleeping the thread is unsuccessful
     */
    public final boolean terminateThread(int millis) throws InterruptedException {
        if (millis <= 0) {
            throw new IllegalArgumentException();
        }
        finish();
        final int iterationLength = 25;
        int counter = 0;
        while (true) {
            if (counter > millis) {
                break;
            }
            Thread.sleep(iterationLength);
            counter += iterationLength;
            if (isAlive() == false) {
                return true;
            }
        }
        return false;
    }

    /**
     * Politely asks the thread to stop, but does not check whether the thread actually terminates
     * in a timely fashion.
     *
     * <p>Generally one should use <code>terminateThread(int)</code> which calls this method and
     * reports on thread termination success or failure.
     */
    public abstract void finish();
}
