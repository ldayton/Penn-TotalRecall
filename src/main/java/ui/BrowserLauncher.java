package ui;

import events.EventDispatchBus;
import events.InfoRequestedEvent;
import events.OpenUrlRequestedEvent;
import events.Subscribe;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Desktop;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles browser launching requests from the UI-agnostic core.
 *
 * <p>This Swing-specific component uses java.awt.Desktop to open URLs in the system browser.
 */
@Singleton
public class BrowserLauncher {
    private static final Logger logger = LoggerFactory.getLogger(BrowserLauncher.class);
    private final EventDispatchBus eventBus;

    @Inject
    public BrowserLauncher(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Subscribe
    public void onOpenUrlRequested(OpenUrlRequestedEvent event) {
        try {
            // Try to use Desktop API to launch browser
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(event.getUrl()));
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to open URL: {}", event.getUrl(), e);
        }

        // If browser launch failed, show the fallback message
        eventBus.publish(new InfoRequestedEvent(event.getFallbackMessage()));
    }
}
