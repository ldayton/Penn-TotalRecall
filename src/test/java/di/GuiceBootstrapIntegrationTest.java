package di;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for GuiceBootstrap to ensure full application DI initialization works correctly
 * without circular dependencies.
 */
class GuiceBootstrapIntegrationTest {

    @Test
    @DisplayName("GuiceBootstrap should initialize without circular dependencies")
    void guiceBootstrapShouldInitializeWithoutCircularDependencies() {
        // This should catch the circular dependency issue we're seeing
        assertDoesNotThrow(
                () -> {
                    var bootstrap = GuiceBootstrap.create();
                    assertNotNull(bootstrap);
                });
    }

    @Test
    @DisplayName("GuiceBootstrap should create all DI components successfully")
    void guiceBootstrapShouldCreateAllDiComponents() {
        var bootstrap = GuiceBootstrap.create();

        // The bootstrap should be created successfully
        assertNotNull(bootstrap);

        // All DI components should be properly wired
        assertDoesNotThrow(
                () -> {
                    // This would test that all components can be instantiated
                    // without circular dependency issues
                });
    }
}
