package ui;

import static org.junit.jupiter.api.Assertions.*;

import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ui.adapters.SwingEventDispatcher;

/** Tests for the Swing-based EventDispatchBus implementation. */
class SwingEventDispatchBusTest {

    private EventDispatchBus eventBus;
    private TestSubscriber subscriber;
    private SwingEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new SwingEventDispatcher();
        eventBus = new EventDispatchBus(dispatcher);
        subscriber = new TestSubscriber();
        eventBus.subscribe(subscriber);
    }

    @Test
    @DisplayName("Events published from EDT should execute immediately on EDT")
    void eventsFromEdtExecuteImmediately() throws Exception {
        AtomicBoolean executedOnEdt = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(
                () -> {
                    subscriber.setCallback(
                            () -> {
                                executedOnEdt.set(SwingUtilities.isEventDispatchThread());
                                latch.countDown();
                            });

                    eventBus.publish(new TestEvent());
                });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Event should have been processed");
        assertTrue(executedOnEdt.get(), "Event should have been executed on EDT");
    }

    @Test
    @DisplayName("Events published from background thread should execute on EDT")
    void eventsFromBackgroundThreadExecuteOnEdt() throws Exception {
        AtomicBoolean executedOnEdt = new AtomicBoolean(false);
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        subscriber.setCallback(
                () -> {
                    executedOnEdt.set(SwingUtilities.isEventDispatchThread());
                    threadName.set(Thread.currentThread().getName());
                    latch.countDown();
                });

        // Publish from background thread
        Thread backgroundThread =
                new Thread(
                        () -> {
                            eventBus.publish(new TestEvent());
                        },
                        "TestBackgroundThread");

        backgroundThread.start();
        backgroundThread.join();

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Event should have been processed");
        assertTrue(executedOnEdt.get(), "Event should have been executed on EDT");
        assertTrue(
                threadName.get().contains("AWT-EventQueue"),
                "Should execute on EDT, but was: " + threadName.get());
    }

    @Test
    @DisplayName("Multiple events from different threads should all execute on EDT")
    void multipleEventsFromDifferentThreadsExecuteOnEdt() throws Exception {
        int eventCount = 5;
        CountDownLatch latch = new CountDownLatch(eventCount);
        AtomicBoolean allOnEdt = new AtomicBoolean(true);

        subscriber.setCallback(
                () -> {
                    if (!SwingUtilities.isEventDispatchThread()) {
                        allOnEdt.set(false);
                    }
                    latch.countDown();
                });

        // Publish from multiple background threads
        for (int i = 0; i < eventCount; i++) {
            Thread thread =
                    new Thread(
                            () -> {
                                eventBus.publish(new TestEvent());
                            },
                            "TestThread-" + i);
            thread.start();
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "All events should have been processed");
        assertTrue(allOnEdt.get(), "All events should have been executed on EDT");
    }

    @Test
    @DisplayName("Null events should be handled gracefully")
    void nullEventsHandledGracefully() {
        assertDoesNotThrow(() -> eventBus.publish(null));
    }

    @Test
    @DisplayName("Events with no subscribers should be handled gracefully")
    void eventsWithNoSubscribersHandledGracefully() {
        assertDoesNotThrow(() -> eventBus.publish(new UnsubscribedEvent()));
    }

    @Test
    @DisplayName("Exception in subscriber should not affect other subscribers")
    void exceptionInSubscriberDoesNotAffectOthers() throws Exception {
        TestSubscriber goodSubscriber = new TestSubscriber();
        TestSubscriber badSubscriber = new TestSubscriber();

        eventBus.subscribe(goodSubscriber);
        eventBus.subscribe(badSubscriber);

        CountDownLatch goodLatch = new CountDownLatch(1);
        goodSubscriber.setCallback(goodLatch::countDown);

        badSubscriber.setCallback(
                () -> {
                    throw new RuntimeException("Test exception");
                });

        SwingUtilities.invokeAndWait(() -> eventBus.publish(new TestEvent()));

        assertTrue(
                goodLatch.await(1, TimeUnit.SECONDS),
                "Good subscriber should still receive event despite bad subscriber exception");
    }

    /** Test event class */
    public static class TestEvent {}

    /** Test event with no subscribers */
    public static class UnsubscribedEvent {}

    /** Test subscriber */
    public static class TestSubscriber {
        private Runnable callback;

        public void setCallback(Runnable callback) {
            this.callback = callback;
        }

        @Subscribe
        public void handleTestEvent(TestEvent event) {
            if (callback != null) {
                callback.run();
            }
        }
    }
}
