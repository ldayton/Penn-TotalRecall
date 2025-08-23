package actions;

import control.AudioState;
import control.ExitRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EventBus;

/** Exits the application with proper cleanup. */
@Singleton
public class ExitAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(ExitAction.class);

    private final AudioState audioState;
    private final EventBus eventBus;

    @Inject
    public ExitAction(AudioState audioState, EventBus eventBus) {
        super("Exit", "Exit the application");
        this.audioState = audioState;
        this.eventBus = eventBus;
    }

    @Override
    protected void performAction(ActionEvent e) {
        // Stop any playing audio
        if (audioState.audioOpen()) {
            try {
                audioState.getPlayer().stop();
            } catch (Exception ex) {
                // Log the error but don't prevent exit
                logger.warn("Error stopping audio during exit: {}", ex.getMessage());
            }
        }

        // Fire exit requested event - UI will handle confirmation dialog
        eventBus.publish(new ExitRequestedEvent());
    }

    @Override
    public void update() {
        // Exit action is always enabled
        setEnabled(true);
    }
}
