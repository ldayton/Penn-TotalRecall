package ui.waveform;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import state.AudioState;

/** Bootstrap waveform mouse interaction after dependency injection resolution. */
@Singleton
public final class WaveformMouseSetup {

    private final WaveformDisplay waveformDisplay;
    private final WaveformCoordinateSystem coordinateSystem;
    private final SelectionOverlay selectionOverlay;
    private final AudioState audioState;

    @Inject
    public WaveformMouseSetup(
            WaveformDisplay waveformDisplay,
            WaveformCoordinateSystem coordinateSystem,
            SelectionOverlay selectionOverlay,
            AudioState audioState) {
        this.waveformDisplay = waveformDisplay;
        this.coordinateSystem = coordinateSystem;
        this.selectionOverlay = selectionOverlay;
        this.audioState = audioState;
        initializeMouseListeners();
    }

    /** Attach mouse handlers for selection and playback interaction. */
    private void initializeMouseListeners() {
        var handler =
                new WaveformSelectionHandler(
                        waveformDisplay, audioState, coordinateSystem, selectionOverlay);
        waveformDisplay.addMouseListener(handler);
        waveformDisplay.addMouseMotionListener(handler);
    }
}
