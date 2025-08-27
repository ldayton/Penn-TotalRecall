package components.waveform;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import state.AudioState;

/** Initializes waveform display components after DI resolution to avoid circular dependencies. */
@Singleton
public class WaveformMouseSetup {

    private final WaveformDisplay waveformDisplay;
    private final WaveformCoordinateSystem waveformCoordinateSystem;
    private final SelectionOverlay selectionOverlay;
    private final AudioState audioState;

    @Inject
    public WaveformMouseSetup(
            WaveformDisplay waveformDisplay,
            WaveformCoordinateSystem waveformCoordinateSystem,
            SelectionOverlay selectionOverlay,
            AudioState audioState) {
        this.waveformDisplay = waveformDisplay;
        this.waveformCoordinateSystem = waveformCoordinateSystem;
        this.selectionOverlay = selectionOverlay;
        this.audioState = audioState;

        // Initialize mouse listeners now that all dependencies are resolved
        initializeMouseListeners();
    }

    private void initializeMouseListeners() {
        WaveformSelectionHandler mouseAdapter =
                new WaveformSelectionHandler(
                        waveformDisplay, audioState, waveformCoordinateSystem, selectionOverlay);

        waveformDisplay.addMouseListener(mouseAdapter);
        waveformDisplay.addMouseMotionListener(mouseAdapter);
    }
}
