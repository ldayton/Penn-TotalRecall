package util;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple event bus for publishing and subscribing to events. */
@Singleton
public class EventBus {
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private final ConcurrentMap<Class<?>, List<Object>> subscribers = new ConcurrentHashMap<>();

    @Inject
    public EventBus() {}

    /**
     * Subscribe an object to events. The object must have methods annotated with @Subscribe that
     * take a single parameter of the event type.
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

    /** Publish an event to all subscribers. */
    public void publish(Object event) {
        List<Object> eventSubscribers = subscribers.get(event.getClass());
        if (eventSubscribers != null) {
            for (Object subscriber : eventSubscribers) {
                try {
                    Method[] methods = subscriber.getClass().getMethods();
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(Subscribe.class)
                                && method.getParameterTypes().length == 1
                                && method.getParameterTypes()[0].isAssignableFrom(
                                        event.getClass())) {
                            method.invoke(subscriber, event);
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.error(
                            "Error publishing event {} to subscriber {}",
                            event.getClass().getSimpleName(),
                            subscriber.getClass().getSimpleName(),
                            e);
                }
            }
        }
    }
}
