package ui.waveform;

import audio.AudioPlayer;
import events.AudioFileSwitchedEvent;
import events.EventDispatchBus;
import events.FocusRequestedEvent;
import events.ScreenSeekRequestedEvent;
import events.Subscribe;
import events.UIUpdateRequestedEvent;
import events.WaveformRefreshEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import state.AudioState;
import ui.annotations.Annotation;
import ui.annotations.AnnotationDisplay;
import waveform.RenderedChunk;
import waveform.Waveform;

/**
 * This WaveformDisplay is totally autonomous except for changes of zoom factor.
 *
 * <p>Keep in mind that events other than the repaint timer going off can cause repaints.
 */
// TODO audio lags behind waveform by a few hundred milliseconds
@Singleton
public final class WaveformDisplay extends JComponent implements WaveformCoordinateSystem {
    private static final Logger logger = LoggerFactory.getLogger(WaveformDisplay.class);

    private static final Color BACKGROUND_COLOR = UIManager.getColor("Panel.background");
    private static final Color TEXT_COLOR = UIManager.getColor("Label.foreground");
    private static final Color REFERENCE_LINE_COLOR = UIManager.getColor("Label.foreground");
    private static final Color PROGRESS_BAR_COLOR = UIManager.getColor("Label.foreground");
    private static final Color ANNOTATION_LINE_COLOR = Color.RED;
    private static final Color ANNOTATION_ACCENT_COLOR = Color.BLUE;

    /** Width in pixels of visualization of one second of audio, prior to any zooming. */
    static final int ZOOMLESS_PIXELS_PER_SECOND = 200;

    /**
     * Number of pixels added/subtracted to zoomless pixels per second for each zoom in/out action.
     */
    static final int ZOOM_AMOUNT = 40;

    private static final BasicStroke PROGRESS_BAR_STROKE =
            new BasicStroke(
                    1,
                    BasicStroke.CAP_SQUARE,
                    BasicStroke.JOIN_BEVEL,
                    0,
                    new float[] {10.0f, 3.0f},
                    0);

    private static final RenderingHints RENDERING_HINTS = createRenderingHints();

    private static RenderingHints createRenderingHints() {
        RenderingHints hints =
                new RenderingHints(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(
                RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        hints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return hints;
    }

    /** Maximum pixels to interpolate when rendering waveform gaps. */
    public static final int MAX_INTERPOLATED_PIXELS = 10;

    /** Time tolerance for interpolation error detection in seconds. */
    public static final double INTERPOLATION_TOLERANCE_SECONDS = 0.25;

    private final DecimalFormat secFormat = new DecimalFormat("0.000s");

    private final int REFRESH_DELAY = 20; // people prefer 20 over 30

    private Timer refreshTimer;
    private Timer resizeDebounceTimer;

    private int pixelsPerSecond;

    private volatile boolean chunkInProgress;

    private long refreshFrame;
    private int refreshWidth;
    private int refreshHeight;
    private RenderedChunk previousRefreshChunk;
    private RenderedChunk curRefreshChunk;
    private RenderedChunk nextRefreshChunk;

    private final AudioState audioState;
    private final EventDispatchBus eventBus;

    private volatile Waveform currentWaveform;

    private volatile int progressBarXPos;
    private int pendingAmplitudeHeight;
    private ImageKey lastImageKey;

    private static record ImageKey(int chunkNum, int timeRes, int height) {}

    @Inject
    public WaveformDisplay(AudioState audioState, EventDispatchBus eventBus) {
        this.audioState = audioState;
        setOpaque(true);
        setBackground(BACKGROUND_COLOR);
        setUI(new ComponentUI() {}); // a little bit of magic so the JComponent will draw the
        // background color without subclassing to a JPanel
        pixelsPerSecond = ZOOMLESS_PIXELS_PER_SECOND;
        refreshFrame = -1;
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        getParent().requestFocusInWindow();
                    }
                });
        // Mouse listeners will be added after DI resolution to avoid circular dependency

        this.eventBus = eventBus;

        // Subscribe to waveform refresh events
        eventBus.subscribe(this);

        // Debounced height update when the component is resized to avoid churn
        addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        pendingAmplitudeHeight = getHeight();
                        if (resizeDebounceTimer != null) {
                            resizeDebounceTimer.restart();
                        }
                    }
                });

        // Debounce timer applies the new height after user stops resizing briefly
        resizeDebounceTimer =
                new Timer(
                        150,
                        _ -> {
                            if (currentWaveform != null && pendingAmplitudeHeight > 0) {
                                currentWaveform.setAmplitudeResolution(pendingAmplitudeHeight);
                                if (refreshTimer == null || !refreshTimer.isRunning()) {
                                    repaint();
                                }
                            }
                        });
        resizeDebounceTimer.setRepeats(false);

        // Initialize last image key tracker
        lastImageKey = null;
    }

    public void zoomX(boolean in) {
        if (in) {
            pixelsPerSecond += ZOOM_AMOUNT;
        } else {
            if (pixelsPerSecond >= ZOOM_AMOUNT + 1) {
                pixelsPerSecond -= ZOOM_AMOUNT;
            }
        }
        if (currentWaveform != null) {
            currentWaveform.setTimeResolution(pixelsPerSecond);
            if (refreshTimer == null || !refreshTimer.isRunning()) {
                repaint();
            }
        }
    }

    public void startRefreshes() {
        ActionListener refresher = new RefreshListener();
        refreshTimer = new Timer(REFRESH_DELAY, refresher);
        refreshTimer.start();
    }

    public void stopRefreshes() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            curRefreshChunk = null;
            previousRefreshChunk = null;
            nextRefreshChunk = null;
            repaint();
        }
    }

    @Subscribe
    public void handleWaveformRefreshEvent(WaveformRefreshEvent event) {
        switch (event.getType()) {
            case START:
                startRefreshes();
                // Create waveform when starting refreshes
                updateCurrentWaveform();
                break;
            case STOP:
                stopRefreshes();
                // Clean up waveform when stopping
                if (currentWaveform != null) {
                    currentWaveform.clearCache();
                    currentWaveform = null;
                }
                break;
        }
    }

    @Subscribe
    public void handleAudioFileSwitchedEvent(AudioFileSwitchedEvent event) {
        // Update waveform when audio file changes
        if (event.file() != null) {
            updateCurrentWaveform();
        } else {
            // Clear waveform when no audio file
            if (currentWaveform != null) {
                currentWaveform.clearCache();
                currentWaveform = null;
            }
        }
    }

    @Subscribe
    public void handleUIUpdateRequestedEvent(UIUpdateRequestedEvent event) {
        if (event.getComponent() == UIUpdateRequestedEvent.Component.WAVEFORM_DISPLAY) {
            repaint();
        }
    }

    @Subscribe
    public void handleScreenSeekRequestedEvent(ScreenSeekRequestedEvent event) {
        int shift = (int) (((double) getWidth() / (double) pixelsPerSecond) * 1000);
        shift -= shift / 5;
        if (event.getDirection() == ScreenSeekRequestedEvent.Direction.BACKWARD) {
            shift *= -1;
        }

        long curFrame = audioState.getAudioProgress();
        long frameShift = audioState.getCalculator().millisToFrames(shift);
        long naivePosition = curFrame + frameShift;
        long frameLength = audioState.getCalculator().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition >= frameLength) {
            finalPosition = frameLength - 1;
        }

        audioState.setAudioProgressAndUpdateActions(finalPosition);
        audioState.getPlayer().playAt(finalPosition);
        eventBus.publish(new FocusRequestedEvent(FocusRequestedEvent.Component.MAIN_WINDOW));
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g); // just so the default background color is painted

        if (refreshTimer == null || curRefreshChunk == null || refreshTimer.isRunning() == false) {
            // draw reference line
            g.setColor(REFERENCE_LINE_COLOR);
            g.drawLine(0, getHeight() / 2, getWidth() - 1, getHeight() / 2);

            // draw bottom border
            g.setColor(UIManager.getColor("Separator.foreground"));
            g.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
            return;
        }
        chunkInProgress = false;

        // draw buffered waveform image
        int curChunkXPos =
                frameToComponentX(audioState.firstFrameOfChunk(curRefreshChunk.chunkNumber()));
        g.drawImage(curRefreshChunk.image(), curChunkXPos, 0, null);

        if (previousRefreshChunk != null) {
            g.drawImage(
                    previousRefreshChunk.image(),
                    curChunkXPos - curRefreshChunk.image().getWidth(null),
                    0,
                    null);
        } else {
            if (curRefreshChunk.chunkNumber() != 0) {
                chunkInProgress = true;
            }
        }
        if (nextRefreshChunk != null) {
            g.drawImage(
                    nextRefreshChunk.image(),
                    curChunkXPos + curRefreshChunk.image().getWidth(null),
                    0,
                    null);
        } else {
            if (curRefreshChunk.chunkNumber() != audioState.lastChunkNum()) {
                chunkInProgress = true;
            }
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHints(RENDERING_HINTS);

        // draw current time
        g2d.drawString(
                secFormat.format(audioState.getCalculator().framesToSec(refreshFrame)), 10, 20);

        // draw annotations
        Annotation[] anns = AnnotationDisplay.getAnnotationsInOrder();
        for (int i = 0; i < anns.length; i++) {
            double time = anns[i].getTime();
            int xPos = frameToComponentX(audioState.getCalculator().millisToFrames(time));
            if (xPos < 0) {
                continue;
            }
            if (xPos > refreshWidth) {
                break;
            }
            String text = anns[i].getText();
            g2d.setColor(ANNOTATION_LINE_COLOR);
            g2d.drawLine(xPos, 0, xPos, getHeight() - 1);
            g2d.setColor(TEXT_COLOR);
            g2d.drawString(text, xPos + 5, 40);
        }

        // find progress bar position
        progressBarXPos = frameToComponentX(refreshFrame);
        if (progressBarXPos < 0) {
            logger.warn("bad val " + progressBarXPos + "/" + (getWidth() - 1));
        } else if (progressBarXPos > getWidth() - 1) {
            if (refreshWidth == getWidth()) {
                if (Math.abs(refreshFrame - audioState.getCalculator().durationInFrames())
                        > audioState
                                .getCalculator()
                                .secondsToFrames(INTERPOLATION_TOLERANCE_SECONDS)) {
                    logger.warn("bad val " + progressBarXPos + "/" + (getWidth() - 1));
                }
            }
            progressBarXPos = getWidth() - 1;
        }

        // accent selected annotation
        boolean foundOverlap = false;
        if (audioState.getPlayer().getStatus() != AudioPlayer.Status.PLAYING) {
            for (int i = 0; i < anns.length; i++) {
                int annX =
                        frameToDisplayXPixel(
                                audioState.getCalculator().millisToFrames(anns[i].getTime()));
                if (progressBarXPos == annX) {
                    foundOverlap = true;
                    g2d.setPaintMode();
                    g2d.setColor(ANNOTATION_ACCENT_COLOR);
                    g2d.drawLine(progressBarXPos, 0, progressBarXPos, refreshHeight - 1);
                    int[] xCoordinates = {
                        progressBarXPos - 20,
                        progressBarXPos - 1,
                        progressBarXPos + 2,
                        progressBarXPos + 20
                    };
                    int[] yCoordinates = {0, 20, 20, 0};
                    g2d.fillPolygon(xCoordinates, yCoordinates, xCoordinates.length);
                    yCoordinates =
                            new int[] {
                                refreshHeight - 1,
                                refreshHeight - 21,
                                refreshHeight - 21,
                                refreshHeight - 1
                            };
                    g2d.fillPolygon(xCoordinates, yCoordinates, xCoordinates.length);
                    break;
                }
            }
        }

        // draw progress bar
        if (foundOverlap == false) {
            Stroke originalStroke = g2d.getStroke();
            g2d.setStroke(PROGRESS_BAR_STROKE);
            g2d.setXORMode(BACKGROUND_COLOR);

            g2d.setColor(PROGRESS_BAR_COLOR);
            g2d.drawLine(progressBarXPos, 0, progressBarXPos, getHeight() - 1);

            g2d.setPaintMode();
            g2d.setStroke(originalStroke);
        }

        // draw bottom border
        g2d.setColor(UIManager.getColor("Separator.foreground"));
        g2d.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
    }

    private int frameToComponentX(long frame) {
        int absoluteX = absoluteX(frame);
        int absoluteCurX = absoluteX(refreshFrame);

        int offset = refreshWidth / 2 - absoluteCurX;
        if (offset > 0) { // first half window of audio is adjusted
            offset = 0;
        } else { // last half window of audio is adjusted
            int absoluteLength =
                    -1
                            * (int)
                                    Math.ceil(
                                            ZOOMLESS_PIXELS_PER_SECOND
                                                    * audioState
                                                            .getCalculator()
                                                            .durationInSeconds());
            if ((-absoluteLength) <= refreshWidth) {
                offset = 0;
            } else {
                offset = Math.max(offset, absoluteLength + refreshWidth);
            }
        }
        return absoluteX + offset;
    }

    private int absoluteX(long frame) {
        return (int) (pixelsPerSecond * audioState.getCalculator().framesToSec(frame));
    }

    public int frameToAbsoluteXPixel(long frame) {
        if (!audioState.audioOpen()) {
            throw new IllegalStateException("audio not open");
        }
        return absoluteX(frame);
    }

    public int frameToDisplayXPixel(long frame) {
        if (!audioState.audioOpen()) {
            throw new IllegalStateException("audio not open");
        }
        return frameToComponentX(frame);
    }

    public int displayXPixelToFrame(int xPix) {
        if (!audioState.audioOpen()) {
            throw new IllegalStateException("audio not open");
        }
        return (int)
                (refreshFrame
                        + (xPix - progressBarXPos)
                                * ((1. / (double) pixelsPerSecond)
                                        * audioState.getCalculator().frameRate()));
    }

    public int getProgressBarXPos() {
        return progressBarXPos;
    }

    // one RefreshListener per file, guaranteed
    protected final class RefreshListener implements ActionListener {
        private final long maxFramesError;
        private final long lastFrame;

        private long bufferedFrame;
        private int bufferedWidth;
        private int bufferedNumAnns;

        private boolean wasPlaying;
        private long lastTime;

        protected RefreshListener() {
            lastFrame = audioState.getCalculator().durationInFrames() - 1;
            maxFramesError =
                    audioState.getCalculator().secondsToFrames(1)
                            / ZOOMLESS_PIXELS_PER_SECOND
                            * MAX_INTERPOLATED_PIXELS;
            bufferedFrame = -1;
            bufferedWidth = -1;
            bufferedNumAnns = -1;
            wasPlaying = false;
            lastTime = 0;
        }

        public final void actionPerformed(ActionEvent evt) {
            long realRefreshFrame = audioState.getAudioProgress();
            refreshWidth = getWidth();
            refreshHeight = getHeight();
            int chunkNum = audioState.lookupChunkNum(realRefreshFrame);
            int numAnns = AnnotationDisplay.getNumAnnotations();
            boolean isPlaying = audioState.getPlayer().getStatus() == AudioPlayer.Status.PLAYING;
            int currentTimeRes =
                    (currentWaveform != null) ? currentWaveform.getTimeResolution() : -1;
            int currentAmpRes =
                    (currentWaveform != null) ? currentWaveform.getAmplitudeResolution() : -1;
            ImageKey currentImageKey = new ImageKey(chunkNum, currentTimeRes, currentAmpRes);

            long curTime = System.nanoTime();
            if (isPlaying && wasPlaying) {
                long changeMillis = curTime - lastTime;
                refreshFrame += audioState.getCalculator().nanosToFrames(changeMillis);
                if (refreshFrame > lastFrame) {
                    refreshFrame = lastFrame;
                }
                if (Math.abs(refreshFrame - realRefreshFrame) > maxFramesError) {
                    if (Math.abs(refreshFrame - lastFrame)
                            > audioState
                                    .getCalculator()
                                    .secondsToFrames(INTERPOLATION_TOLERANCE_SECONDS)) {
                        logger.warn(
                                "interpolation error greater than "
                                        + MAX_INTERPOLATED_PIXELS
                                        + " pixels: "
                                        + Math.abs(refreshFrame - realRefreshFrame)
                                        + " (frames)");
                        refreshFrame = realRefreshFrame;
                    }
                }
            } else {
                refreshFrame = realRefreshFrame;
            }
            lastTime = curTime;

            if (chunkInProgress == false
                    && Objects.equals(currentImageKey, lastImageKey)
                    && refreshFrame == bufferedFrame
                    && bufferedWidth == refreshWidth
                    && bufferedNumAnns == numAnns) {
                return;
            }

            if (currentWaveform != null) {
                curRefreshChunk = currentWaveform.renderChunk(chunkNum);
                if (chunkNum > 0) {
                    previousRefreshChunk = currentWaveform.renderChunk(chunkNum - 1);
                } else {
                    previousRefreshChunk = null;
                }
                if (chunkNum < audioState.lastChunkNum()) {
                    nextRefreshChunk = currentWaveform.renderChunk(chunkNum + 1);
                } else {
                    nextRefreshChunk = null;
                }
            } else {
                curRefreshChunk = null;
                previousRefreshChunk = null;
                nextRefreshChunk = null;
            }

            wasPlaying = isPlaying;
            bufferedFrame = realRefreshFrame;
            bufferedWidth = refreshWidth;
            bufferedNumAnns = AnnotationDisplay.getNumAnnotations();
            lastImageKey = currentImageKey;

            repaint();
        }
    }

    // WaveformGeometry interface implementation
    @Override
    public Component asComponent() {
        return this;
    }

    /** Creates a new waveform for the current audio file with proper height. */
    private Waveform createWaveformForCurrentFile() {
        if (!audioState.audioOpen()) {
            return null;
        }

        return Waveform.builder(audioState.getFmodCore())
                .audioFile(audioState.getCurrentAudioFileAbsolutePath())
                .timeResolution(pixelsPerSecond)
                .amplitudeResolution(getHeight()) // Use actual component height
                .enableCaching(true)
                .build();
    }

    /** Updates the current waveform and initializes its cache. */
    private void updateCurrentWaveform() {
        // Clear old waveform cache if present
        if (currentWaveform != null) {
            currentWaveform.clearCache();
        }

        // Create new waveform with current height
        currentWaveform = createWaveformForCurrentFile();

        // Initialize cache if waveform was created
        if (currentWaveform != null) {
            currentWaveform.initializeCache(audioState);
        }
    }
}
