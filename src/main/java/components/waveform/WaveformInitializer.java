package components.waveform;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import state.AudioState;

/** Initializes waveform display components after DI resolution to avoid circular dependencies. */
@Singleton
public class WaveformInitializer {

    private final WaveformDisplay waveformDisplay;
    private final WaveformGeometry waveformGeometry;
    private final SelectionOverlay selectionOverlay;
    private final AudioState audioState;

    @Inject
    public WaveformInitializer(
            WaveformDisplay waveformDisplay,
            WaveformGeometry waveformGeometry,
            SelectionOverlay selectionOverlay,
            AudioState audioState) {
        this.waveformDisplay = waveformDisplay;
        this.waveformGeometry = waveformGeometry;
        this.selectionOverlay = selectionOverlay;
        this.audioState = audioState;

        // Initialize mouse listeners now that all dependencies are resolved
        initializeMouseListeners();
    }

    private void initializeMouseListeners() {
        WaveformMouseAdapter mouseAdapter =
                new WaveformMouseAdapter(
                        waveformDisplay, audioState, waveformGeometry, selectionOverlay);

        waveformDisplay.addMouseListener(mouseAdapter);
        waveformDisplay.addMouseMotionListener(mouseAdapter);
    }
}
