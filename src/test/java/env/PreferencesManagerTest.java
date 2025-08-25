package env;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import state.PreferencesManager;

class PreferencesManagerTest {

    private PreferencesManager prefsManager;
    private UserHomeProvider mockUserHomeProvider;
    private String testNamespace;
    private Preferences testPrefs;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        mockUserHomeProvider = mock(UserHomeProvider.class);
        when(mockUserHomeProvider.getUserHomeDir()).thenReturn("/home/test");

        // Set up log capture
        logger = (Logger) LoggerFactory.getLogger(PreferencesManager.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        // Use unique namespace per test to ensure complete isolation
        testNamespace = "/test/penntotalrecall/" + UUID.randomUUID();
        prefsManager = new PreferencesManager(mockUserHomeProvider, testNamespace);

        // Clear test namespace for clean state
        testPrefs = Preferences.userRoot().node(testNamespace);
        try {
            testPrefs.clear();
        } catch (BackingStoreException e) {
            throw new RuntimeException("Failed to clear test preferences", e);
        }
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("getString returns stored value")
    void getStringReturnsStoredValue() {
        testPrefs.put("test.key", "stored.value");

        assertEquals("stored.value", prefsManager.getString("test.key", "default"));
    }

    @Test
    @DisplayName("getString returns default when missing")
    void getStringReturnsDefaultWhenMissing() {
        assertEquals("default.value", prefsManager.getString("missing.key", "default.value"));
    }

    @Test
    @DisplayName("putString stores value")
    void putStringStoresValue() {
        prefsManager.putString("new.key", "new.value");

        assertEquals("new.value", testPrefs.get("new.key", null));
    }

    @Test
    @DisplayName("getInt returns valid stored integer")
    void getIntReturnsStoredInteger() {
        testPrefs.put("int.key", "42");

        assertEquals(42, prefsManager.getInt("int.key", 100));
    }

    @Test
    @DisplayName("getInt deletes malformed value and returns default")
    void getIntDeletesMalformedValue() {
        testPrefs.put("bad.int", "not.a.number");

        assertEquals(456, prefsManager.getInt("bad.int", 456));
        assertNull(testPrefs.get("bad.int", null), "Malformed value should be deleted");
    }

    @Test
    @DisplayName("getInt handles integer edge cases")
    void getIntHandlesEdgeCases() {
        testPrefs.put("max.int", String.valueOf(Integer.MAX_VALUE));
        testPrefs.put("min.int", String.valueOf(Integer.MIN_VALUE));
        testPrefs.put("zero", "0");

        assertEquals(Integer.MAX_VALUE, prefsManager.getInt("max.int", 1));
        assertEquals(Integer.MIN_VALUE, prefsManager.getInt("min.int", 1));
        assertEquals(0, prefsManager.getInt("zero", 1));
    }

    @Test
    @DisplayName("getBoolean returns valid stored boolean")
    void getBooleanReturnsStoredBoolean() {
        testPrefs.put("bool.true", "true");
        testPrefs.put("bool.false", "false");
        testPrefs.put("bool.mixed", "True");

        assertTrue(prefsManager.getBoolean("bool.true", false));
        assertFalse(prefsManager.getBoolean("bool.false", true));
        assertTrue(prefsManager.getBoolean("bool.mixed", false));
    }

    @Test
    @DisplayName("getBoolean deletes malformed value and returns default")
    void getBooleanDeletesMalformedValue() {
        testPrefs.put("bad.bool", "maybe");

        assertTrue(prefsManager.getBoolean("bad.bool", true));
        assertNull(testPrefs.get("bad.bool", null), "Malformed value should be deleted");
    }

    @Test
    @DisplayName("getFloat returns valid stored float")
    void getFloatReturnsStoredFloat() {
        testPrefs.put("float.key", "3.14159");

        assertEquals(3.14159f, prefsManager.getFloat("float.key", 1.0f), 0.00001f);
    }

    @Test
    @DisplayName("getFloat deletes malformed value and returns default")
    void getFloatDeletesMalformedValue() {
        testPrefs.put("bad.float", "not.a.float");

        assertEquals(2.5f, prefsManager.getFloat("bad.float", 2.5f));
        assertNull(testPrefs.get("bad.float", null), "Malformed value should be deleted");
    }

    @Test
    @DisplayName("getLong returns valid stored long")
    void getLongReturnsStoredLong() {
        testPrefs.put("long.key", String.valueOf(Long.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, prefsManager.getLong("long.key", 100L));
    }

    @Test
    @DisplayName("getLong deletes malformed value and returns default")
    void getLongDeletesMalformedValue() {
        testPrefs.put("bad.long", "not.a.long");

        assertEquals(789L, prefsManager.getLong("bad.long", 789L));
        assertNull(testPrefs.get("bad.long", null), "Malformed value should be deleted");
    }

    @Test
    @DisplayName("getValidatedPath returns existing path")
    void getValidatedPathReturnsExistingPath() throws IOException {
        Path testFile = tempDir.resolve("existing-file.txt");
        Files.createFile(testFile);
        String existingPath = testFile.toString();

        testPrefs.put("path.key", existingPath);

        assertEquals(existingPath, prefsManager.getValidatedPath("path.key", "/fallback"));
    }

    @Test
    @DisplayName("getValidatedPath returns fallback when path doesn't exist")
    void getValidatedPathReturnsFallbackWhenPathMissing() {
        String nonExistentPath = tempDir.resolve("does-not-exist").toString();
        testPrefs.put("path.key", nonExistentPath);

        assertEquals("/fallback/path", prefsManager.getValidatedPath("path.key", "/fallback/path"));
    }

    @Test
    @DisplayName("getValidatedPath returns fallback when preference missing")
    void getValidatedPathReturnsFallbackWhenPreferenceMissing() {
        assertEquals(
                "/fallback/path", prefsManager.getValidatedPath("missing.path", "/fallback/path"));
    }

    @Test
    @DisplayName("getPathWithHomeFallback uses UserHomeProvider home directory")
    void getPathWithHomeFallbackUsesUserHomeProvider() {
        assertEquals("/home/test", prefsManager.getPathWithHomeFallback("missing.path"));
        verify(mockUserHomeProvider).getUserHomeDir();
    }

    @Test
    @DisplayName("putDirectoryPath stores directory path unchanged")
    void putDirectoryPathStoresDirectoryAsIs() throws IOException {
        Path testDir = tempDir.resolve("test-directory");
        Files.createDirectory(testDir);

        prefsManager.putDirectoryPath("dir.key", testDir.toString());

        assertEquals(testDir.toString(), testPrefs.get("dir.key", null));
    }

    @Test
    @DisplayName("putDirectoryPath stores parent when given file")
    void putDirectoryPathStoresParentForFile() throws IOException {
        Path testFile = tempDir.resolve("test-file.txt");
        Files.createFile(testFile);

        prefsManager.putDirectoryPath("dir.key", testFile.toString());

        assertEquals(tempDir.toString(), testPrefs.get("dir.key", null));
    }

    @Test
    @DisplayName("putDirectoryPath handles file with no parent")
    void putDirectoryPathHandlesNoParent() {
        // Create a mock File that returns null for getParentFile()
        String rootFile = "/";

        prefsManager.putDirectoryPath("dir.key", rootFile);

        assertEquals(rootFile, testPrefs.get("dir.key", null));
    }

    @Test
    @DisplayName("all types return defaults for missing preferences")
    void allTypesReturnDefaultsForMissingPreferences() {
        assertEquals("default", prefsManager.getString("missing", "default"));
        assertEquals(42, prefsManager.getInt("missing", 42));
        assertTrue(prefsManager.getBoolean("missing", true));
        assertEquals(3.14f, prefsManager.getFloat("missing", 3.14f));
        assertEquals(999L, prefsManager.getLong("missing", 999L));
        assertEquals("/fallback", prefsManager.getValidatedPath("missing", "/fallback"));
    }

    @Test
    @DisplayName("preferences are isolated to test namespace")
    void preferencesAreIsolatedToTestNamespace() {
        prefsManager.putString("isolated.key", "test.value");

        // Verify it's NOT in production namespace
        Preferences prodPrefs =
                Preferences.userRoot().node("/edu/upenn/psych/memory/penntotalrecall");
        assertNull(prodPrefs.get("isolated.key", null), "Should not leak to production namespace");

        // Verify it IS in test namespace
        assertEquals("test.value", testPrefs.get("isolated.key", null));
    }

    @Test
    @DisplayName("malformed values are cleaned up across all types")
    void malformedValuesAreCleanedUpAcrossAllTypes() {
        // Set malformed values
        testPrefs.put("bad.int", "not-int");
        testPrefs.put("bad.bool", "not-bool");
        testPrefs.put("bad.float", "not-float");
        testPrefs.put("bad.long", "not-long");

        // Access them (should trigger cleanup)
        prefsManager.getInt("bad.int", 1);
        prefsManager.getBoolean("bad.bool", false);
        prefsManager.getFloat("bad.float", 1.0f);
        prefsManager.getLong("bad.long", 1L);

        // Verify all were deleted
        assertNull(testPrefs.get("bad.int", null));
        assertNull(testPrefs.get("bad.bool", null));
        assertNull(testPrefs.get("bad.float", null));
        assertNull(testPrefs.get("bad.long", null));
    }

    @Test
    @DisplayName("flush does not throw exceptions")
    void flushDoesNotThrowExceptions() {
        prefsManager.putString("test.key", "test.value");

        assertDoesNotThrow(() -> prefsManager.flush());
    }

    @Test
    @DisplayName("constructor validates UserHomeProvider is not null")
    void constructorValidatesUserHomeProviderNotNull() {
        assertThrows(NullPointerException.class, () -> new PreferencesManager(null));
    }

    @Test
    @DisplayName("getString logs warning when using default")
    void getStringLogsWarningWhenUsingDefault() {
        prefsManager.getString("missing.key", "default");

        List<ILoggingEvent> logEvents = logAppender.list;
        assertEquals(1, logEvents.size());
        assertEquals(Level.WARN, logEvents.get(0).getLevel());
        assertTrue(logEvents.get(0).getMessage().contains("not found"));
    }

    @Test
    @DisplayName("malformed int logs warning and cleanup")
    void malformedIntLogsWarningAndCleanup() {
        testPrefs.put("bad.int", "garbage");

        prefsManager.getInt("bad.int", 42);

        List<ILoggingEvent> logEvents = logAppender.list;
        assertTrue(
                logEvents.stream()
                        .anyMatch(
                                event ->
                                        event.getLevel() == Level.WARN
                                                && event.getMessage().contains("malformed value")));
    }

    @Test
    @DisplayName("concurrent access is thread-safe")
    void concurrentAccessIsThreadSafe() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            CompletableFuture<Void>[] futures = new CompletableFuture[100];

            for (int i = 0; i < 100; i++) {
                final int index = i;
                futures[i] =
                        CompletableFuture.runAsync(
                                () -> {
                                    prefsManager.putInt("concurrent.key." + index, index);
                                    prefsManager.getInt("concurrent.key." + index, -1);
                                },
                                executor);
            }

            CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);

            // Verify all values were stored correctly
            for (int i = 0; i < 100; i++) {
                assertEquals(i, prefsManager.getInt("concurrent.key." + i, -1));
            }
        } catch (Exception e) {
            fail("Concurrent access failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("getValidatedPath handles empty path")
    void getValidatedPathHandlesEmptyPath() {
        testPrefs.put("empty.path", "");

        assertEquals("/fallback", prefsManager.getValidatedPath("empty.path", "/fallback"));
    }

    @Test
    @DisplayName("getValidatedPath handles null path from preferences")
    void getValidatedPathHandlesNullPath() {
        // Don't set any preference, should get null from prefs.get()
        assertEquals("/fallback", prefsManager.getValidatedPath("null.path", "/fallback"));

        List<ILoggingEvent> logEvents = logAppender.list;
        assertTrue(
                logEvents.stream()
                        .anyMatch(
                                event ->
                                        event.getLevel() == Level.WARN
                                                && event.getMessage().contains("not found")));
    }

    @Test
    @DisplayName("putDirectoryPath handles empty path")
    void putDirectoryPathHandlesEmptyPath() {
        prefsManager.putDirectoryPath("empty.dir", "");

        assertEquals("", testPrefs.get("empty.dir", null));
    }

    @Test
    @DisplayName("float edge cases are handled correctly")
    void floatEdgeCasesHandledCorrectly() {
        testPrefs.put("float.infinity", "Infinity");
        testPrefs.put("float.nan", "NaN");
        testPrefs.put("float.negative.zero", "-0.0");

        assertEquals(Float.POSITIVE_INFINITY, prefsManager.getFloat("float.infinity", 1.0f));
        assertTrue(Float.isNaN(prefsManager.getFloat("float.nan", 1.0f)));
        assertEquals(-0.0f, prefsManager.getFloat("float.negative.zero", 1.0f));
    }

    @Test
    @DisplayName("boolean case variations are handled")
    void booleanCaseVariationsHandled() {
        testPrefs.put("bool.TRUE", "TRUE");
        testPrefs.put("bool.False", "False");
        testPrefs.put("bool.tRuE", "tRuE");

        assertTrue(prefsManager.getBoolean("bool.TRUE", false));
        assertFalse(prefsManager.getBoolean("bool.False", true));
        assertTrue(prefsManager.getBoolean("bool.tRuE", false));
    }
}
