package app.headless;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertSame;

import core.actions.ActionRegistry;
import core.dispatch.EventDispatchBus;
import org.junit.jupiter.api.Test;

/** Test to verify HeadlessTestFixture works correctly. */
class HeadlessTestFixtureTest extends HeadlessTestFixture {

    @Test
    void canGetInstancesFromContainer() {
        // Core services should be available
        EventDispatchBus eventBus = getInstance(EventDispatchBus.class);
        assertNotNull(eventBus, "EventDispatchBus should be available");

        ActionRegistry actionRegistry = getInstance(ActionRegistry.class);
        assertNotNull(actionRegistry, "ActionRegistry should be available");
    }

    @Test
    void headlessModeIsActive() {
        assertTrue(java.awt.GraphicsEnvironment.isHeadless(), "Should be running in headless mode");
    }

    @Test
    void canGetInstanceWithDefault() {
        // For a class that definitely won't be bound
        // Using a custom class that isn't in the DI container
        class UnboundClass {}
        UnboundClass defaultValue = new UnboundClass();
        UnboundClass result = getInstance(UnboundClass.class, defaultValue);
        assertSame(defaultValue, result, "Should return default for unbound class");
    }
}
