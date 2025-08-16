package annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Test annotation for packaging integration tests.
 *
 * <p>Tests annotated with {@code @Packaging} require built application artifacts (e.g., .app
 * bundles, DMG files) to be present before execution. These tests should be run via the {@code
 * packageTest} Gradle target, which ensures all necessary build dependencies are satisfied.
 *
 * <p>Contract: Gradle MUST provide required artifacts before test execution. Tests will FAIL (not
 * skip) if preconditions are not met, as it indicates a build system configuration error.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Test
 * @Packaging
 * void testPackagedApplication() {
 *     // Test assumes .app bundle exists - fails if missing
 *     var appBundle = Paths.get("build/packaging/mac/Penn TotalRecall.app");
 *     assertTrue(Files.exists(appBundle), "App bundle must exist for packaging tests");
 *     // ... rest of test
 * }
 * }</pre>
 *
 * <p>Run with: {@code ./gradlew packageTest}
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("packaging")
public @interface Packaging {}
