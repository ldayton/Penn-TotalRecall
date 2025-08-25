package components.waveform;

import audio.AudioPlayer;
import components.annotations.Annotation;
import components.annotations.AnnotationDisplay;
import components.waveform.WaveformBuffer.WaveformChunk;
import control.AudioState;
import events.FocusRequestedEvent;
import events.ScreenSeekRequestedEvent;
import events.UIUpdateRequestedEvent;
import events.WaveformRefreshEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.plaf.ComponentUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventDispatchBus;
import util.GUIConstants;
import util.Subscribe;
import util.UiColors;
import util.UiShapes;

/**
 * This WaveformDisplay is totally autonomous except for changes of zoom factor.
 *
 * <p>Keep in mind that events other than the repaint timer going off can cause repaints.
 */
@Singleton
public class WaveformDisplay extends JComponent {
    private static final Logger logger = LoggerFactory.getLogger(WaveformDisplay.class);

    /** Maximum pixels to interpolate when rendering waveform gaps. */
    public static final int MAX_INTERPOLATED_PIXELS = 10;

    /** Time tolerance for interpolation error detection in seconds. */
    public static final double INTERPOLATION_TOLERANCE_SECONDS = 0.25;

    private final DecimalFormat secFormat = new DecimalFormat("0.000s");

    private final int REFRESH_DELAY = 20; // people prefer 20 over 30

    private Timer refreshTimer;

    private int pixelsPerSecond;

    private volatile boolean chunkInProgress;

    private static volatile int progressBarXPos;

    private long refreshFrame;
    private int refreshWidth;
    private int refreshHeight;
    private WaveformChunk previousRefreshChunk;
    private WaveformChunk curRefreshChunk;
    private WaveformChunk nextRefreshChunk;

    private static WaveformDisplay instance;
    private static AudioState audioState;
    private final EventDispatchBus eventBus;

    @Inject
    public WaveformDisplay(AudioState audioState, EventDispatchBus eventBus) {
        WaveformDisplay.audioState = audioState;
        setOpaque(true);
        setBackground(UiColors.waveformBackground);
        setUI(new ComponentUI() {}); // a little bit of magic so the JComponent will draw the
        // background color without subclassing to a JPanel
        pixelsPerSecond = GUIConstants.zoomlessPixelsPerSecond;
        refreshFrame = -1;
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        getParent().requestFocusInWindow();
                    }
                });
        addMouseListener(new WaveformMouseAdapter(this, audioState));
        addMouseMotionListener(new WaveformMouseAdapter(this, audioState));

        this.eventBus = eventBus;

        // Subscribe to waveform refresh events
        eventBus.subscribe(this);

        // Set the singleton instance after full initialization
        instance = this;
    }

    public static WaveformDisplay getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "WaveformDisplay not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }

    public static int height() {
        return instance.getHeight();
    }

    public static void zoomX(boolean in) {
        if (in) {
            instance.pixelsPerSecond += GUIConstants.xZoomAmount;
        } else {
            if (instance.pixelsPerSecond >= GUIConstants.xZoomAmount + 1) {
                instance.pixelsPerSecond -= GUIConstants.xZoomAmount;
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
                break;
            case STOP:
                stopRefreshes();
                break;
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
        int shift =
                (int)
                        (((double) getWidth() / (double) GUIConstants.zoomlessPixelsPerSecond)
                                * 1000);
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
            g.setColor(UiColors.waveformReferenceLineColor);
            g.drawLine(0, getHeight() / 2, getWidth() - 1, getHeight() / 2);

            // draw bottom border
            g.setColor(UiColors.unfocusedColor);
            g.drawLine(0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
            return;
        }
        chunkInProgress = false;

        // draw buffered waveform image
        int curChunkXPos =
                frameToComponentX(audioState.firstFrameOfChunk(curRefreshChunk.getNum()));
        g.drawImage(curRefreshChunk.getImage(), curChunkXPos, 0, null);

        if (previousRefreshChunk != null) {
            g.drawImage(
                    previousRefreshChunk.getImage(),
                    curChunkXPos - curRefreshChunk.getImage().getWidth(null),
                    0,
                    null);
        } else {
            if (curRefreshChunk.getNum() != 0) {
                chunkInProgress = true;
            }
        }
        if (nextRefreshChunk != null) {
            g.drawImage(
                    nextRefreshChunk.getImage(),
                    curChunkXPos + curRefreshChunk.getImage().getWidth(null),
                    0,
                    null);
        } else {
            if (curRefreshChunk.getNum() != audioState.lastChunkNum()) {
                chunkInProgress = true;
            }
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHints(UiShapes.getRenderingHints());

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
            g2d.setColor(UiColors.annotationLineColor);
            g2d.drawLine(xPos, 0, xPos, getHeight() - 1);
            g2d.setColor(UiColors.annotationTextColor);
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
                        WaveformDisplay.frameToDisplayXPixel(
                                audioState.getCalculator().millisToFrames(anns[i].getTime()));
                if (progressBarXPos == annX) {
                    foundOverlap = true;
                    g2d.setPaintMode();
                    g2d.setColor(UiColors.annotationAccentColor);
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
            g2d.setStroke(UiShapes.getProgressBarStroke());
            g2d.setXORMode(UiColors.waveformBackground);

            g2d.setColor(UiColors.progressBarColor);
            g2d.drawLine(progressBarXPos, 0, progressBarXPos, getHeight() - 1);

            g2d.setPaintMode();
            g2d.setStroke(originalStroke);
        }

        // draw bottom border
        g2d.setColor(UiColors.unfocusedColor);
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
                                            GUIConstants.zoomlessPixelsPerSecond
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
        return (int)
                (GUIConstants.zoomlessPixelsPerSecond
                        * audioState.getCalculator().framesToSec(frame));
    }

    public static int frameToAbsoluteXPixel(long frame) {
        if (audioState.audioOpen()) {
            return instance.absoluteX(frame);
        }
        throw new IllegalStateException("audio not open");
    }

    public static int frameToDisplayXPixel(long frame) {
        if (audioState.audioOpen()) {
            return instance.frameToComponentX(frame);
        }
        throw new IllegalStateException("audio not open");
    }

    public static int displayXPixelToFrame(int xPix) {
        if (audioState.audioOpen()) {
            return (int)
                    (instance.refreshFrame
                            + (xPix - progressBarXPos)
                                    * ((1. / GUIConstants.zoomlessPixelsPerSecond)
                                            * audioState.getCalculator().frameRate()));
        }
        throw new IllegalStateException("audio not open");
    }

    public static int getProgressBarXPos() {
        return progressBarXPos;
    }

    // one RefreshListener per file, guaranteed
    protected final class RefreshListener implements ActionListener {
        private final long maxFramesError;
        private final long lastFrame;

        private long bufferedFrame;
        private int bufferedWidth;
        private int bufferedHeight;
        private int bufferedNumAnns;

        private boolean wasPlaying;
        private long lastTime;

        protected RefreshListener() {
            lastFrame = audioState.getCalculator().durationInFrames() - 1;
            maxFramesError =
                    audioState.getCalculator().secondsToFrames(1)
                            / GUIConstants.zoomlessPixelsPerSecond
                            * MAX_INTERPOLATED_PIXELS;
            bufferedFrame = -1;
            bufferedWidth = -1;
            bufferedHeight = -1;
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
                    && refreshFrame == bufferedFrame
                    && bufferedWidth == refreshWidth
                    && bufferedHeight == refreshHeight
                    && bufferedNumAnns == numAnns) {
                return;
            }

            WaveformChunk[] chunks = WaveformBuffer.getWaveformChunks();
            if (chunks == null) { // occurs only while WaveformBuffer's constructor is being run
                return;
            }
            if (chunks[chunkNum] == null) {
                return;
            }
            curRefreshChunk = chunks[chunkNum];
            if (chunkNum > 0) {
                previousRefreshChunk = chunks[chunkNum - 1];
            }
            if (chunkNum < chunks.length - 1) {
                nextRefreshChunk = chunks[chunkNum + 1];
            }

            wasPlaying = isPlaying;
            bufferedFrame = realRefreshFrame;
            bufferedWidth = refreshWidth;
            bufferedHeight = curRefreshChunk.getImage().getHeight(null);
            bufferedNumAnns = AnnotationDisplay.getNumAnnotations();

            repaint();
        }
    }
}
