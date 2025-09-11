package annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks tests that require windowing system and display capabilities.
 *
 * <p>Tests annotated with this will be automatically excluded in headless CI environments where no
 * display is available. This prevents GUI-related tests from failing in environments that cannot
 * support windowing systems.
 *
 * <p>The exclusion is controlled by build configuration based on the CI environment.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("windowing")
public @interface Windowing {}
