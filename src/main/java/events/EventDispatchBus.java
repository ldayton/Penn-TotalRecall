package events;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe event bus for publishing and subscribing to events.
 *
 * <h3>Threading Model</h3>
 *
 * <ul>
 *   <li>Events can be published from any thread
 *   <li>All subscribers are executed on the Event Dispatch Thread (EDT)
 *   <li>If published from EDT, subscribers execute immediately
 *   <li>If published from other threads, subscribers are queued to EDT
 * </ul>
 *
 * This design ensures thread safety for Swing applications where most event handlers need to update
 * UI components.
 */
@Singleton
public class EventDispatchBus {
    private static final Logger logger = LoggerFactory.getLogger(EventDispatchBus.class);

    private final ConcurrentMap<Class<?>, List<Object>> subscribers = new ConcurrentHashMap<>();

    @Inject
    public EventDispatchBus() {}

    /**
     * Subscribe an object to events. The object must have methods annotated with @Subscribe that
     * take a single parameter of the event type.
     *
     * @param subscriber The object containing @Subscribe methods
     */
    public void subscribe(Object subscriber) {
        Method[] methods = subscriber.getClass().getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Subscribe.class)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1) {
                    Class<?> eventType = parameterTypes[0];
                    subscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(subscriber);
                    logger.debug(
                            "Subscribed {} to {}",
                            subscriber.getClass().getSimpleName(),
                            eventType.getSimpleName());
                }
            }
        }
    }

    /**
     * Publish an event to all subscribers.
     *
     * <p>Events can be published from any thread. All subscribers will be executed on the Event
     * Dispatch Thread (EDT) to ensure thread safety for UI operations.
     *
     * @param event The event to publish
     */
    public void publish(Object event) {
        if (event == null) {
            logger.warn("Attempted to publish null event");
            return;
        }

        List<Object> eventSubscribers = subscribers.get(event.getClass());
        if (eventSubscribers == null || eventSubscribers.isEmpty()) {
            logger.trace("No subscribers for event type: {}", event.getClass().getSimpleName());
            return;
        }

        // Create a snapshot of subscribers to avoid concurrent modification
        List<Object> subscriberSnapshot = new ArrayList<>(eventSubscribers);

        Runnable notificationTask = () -> notifySubscribers(event, subscriberSnapshot);

        if (SwingUtilities.isEventDispatchThread()) {
            // Already on EDT, execute immediately
            notificationTask.run();
        } else {
            // Queue for execution on EDT
            SwingUtilities.invokeLater(notificationTask);
            logger.trace("Queued event {} for EDT execution", event.getClass().getSimpleName());
        }
    }

    /**
     * Notify all subscribers of an event. This method must be called on the EDT.
     *
     * @param event The event to deliver
     * @param eventSubscribers The list of subscribers to notify
     */
    private void notifySubscribers(Object event, List<Object> eventSubscribers) {
        if (!SwingUtilities.isEventDispatchThread()) {
            logger.error(
                    "notifySubscribers called from non-EDT thread: {}",
                    Thread.currentThread().getName());
            return;
        }

        for (Object subscriber : eventSubscribers) {
            try {
                Method[] methods = subscriber.getClass().getMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(Subscribe.class)
                            && method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                        method.invoke(subscriber, event);
                        logger.trace(
                                "Delivered event {} to {}",
                                event.getClass().getSimpleName(),
                                subscriber.getClass().getSimpleName());
                        break;
                    }
                }
            } catch (Exception e) {
                logger.error(
                        "Error delivering event {} to subscriber {}",
                        event.getClass().getSimpleName(),
                        subscriber.getClass().getSimpleName(),
                        e);
                // Continue processing other subscribers even if one fails
            }
        }
    }
}
