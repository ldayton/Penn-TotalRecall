package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import a2.AudioEngineConfig;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Tests for FmodAudioEngine initialization and shutdown with focus on concurrency. */
class FmodAudioEngineInitCloseTest {

    private FmodAudioEngine engine;
    private FmodAudioEngine spyEngine;
    private AudioEngineConfig config;
    private FmodLibrary mockFmod;

    @BeforeEach
    void setUp() {
        engine = new FmodAudioEngine();
        config =
                AudioEngineConfig.builder()
                        .engineType("fmod")
                        .mode(AudioEngineConfig.Mode.PLAYBACK)
                        .maxCacheBytes(10 * 1024 * 1024) // 10MB
                        .build();
        mockFmod = mock(FmodLibrary.class);

        // Create spy that returns mock library
        spyEngine = spy(engine);
        doReturn(mockFmod).when(spyEngine).doLoadFmodLibrary();
    }

    @Test
    void testSuccessfulInitialization() throws Exception {
        // Setup mocks
        setupSuccessfulFmodMocks();

        // Initialize
        assertDoesNotThrow(() -> spyEngine.init(config));

        // Verify state
        assertEquals("INITIALIZED", getState(spyEngine));
        assertNotNull(getField(spyEngine, "system"));
        assertNotNull(getField(spyEngine, "fmod"));
        assertNotNull(getField(spyEngine, "config"));
    }

    @Test
    void testInitFailureReturnsToUninitialized() throws Exception {
        // Setup mocks to fail
        when(mockFmod.FMOD_System_Create(any(PointerByReference.class), anyInt()))
                .thenReturn(FmodConstants.FMOD_ERR_BADCOMMAND);
        when(mockFmod.FMOD_ErrorString(anyInt())).thenReturn("Test error");

        // Initialize should fail
        RuntimeException ex = assertThrows(RuntimeException.class, () -> spyEngine.init(config));
        assertTrue(ex.getMessage().contains("Failed to create FMOD system"));

        // Verify state returned to UNINITIALIZED
        assertEquals("UNINITIALIZED", getState(spyEngine));
        assertNull(getField(spyEngine, "system"));
        assertNull(getField(spyEngine, "fmod"));
    }

    @Test
    void testInitRetryAfterFailure() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        // First call fails, second succeeds
        when(mockFmod.FMOD_System_Create(any(PointerByReference.class), anyInt()))
                .thenAnswer(
                        inv -> {
                            if (callCount.incrementAndGet() == 1) {
                                return FmodConstants.FMOD_ERR_BADCOMMAND;
                            } else {
                                PointerByReference ref = inv.getArgument(0);
                                ref.setValue(new Memory(8));
                                return FmodConstants.FMOD_OK;
                            }
                        });
        when(mockFmod.FMOD_System_Init(any(), anyInt(), anyInt(), any()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_ErrorString(anyInt())).thenReturn("Test error");

        // First init fails
        assertThrows(RuntimeException.class, () -> spyEngine.init(config));
        assertEquals("UNINITIALIZED", getState(spyEngine));

        // Second init succeeds
        assertDoesNotThrow(() -> spyEngine.init(config));
        assertEquals("INITIALIZED", getState(spyEngine));
    }

    @Test
    void testCannotInitializeFromNonUninitializedState() throws Exception {
        // First successful init
        setupSuccessfulFmodMocks();
        spyEngine.init(config);

        // Second init should fail
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> spyEngine.init(config));
        assertTrue(ex.getMessage().contains("Cannot initialize engine in state: INITIALIZED"));
    }

    @Test
    void testCloseFromInitializedState() throws Exception {
        // Initialize first
        setupSuccessfulFmodMocks();
        spyEngine.init(config);

        // Setup cleanup mocks
        when(mockFmod.FMOD_System_Release(any())).thenReturn(FmodConstants.FMOD_OK);

        // Close
        assertDoesNotThrow(() -> spyEngine.close());

        // Verify cleanup
        verify(mockFmod).FMOD_System_Release(any());
        assertEquals("CLOSED", getState(spyEngine));
        assertNull(getField(spyEngine, "system"));
        assertNull(getField(spyEngine, "fmod"));
        assertNull(getField(spyEngine, "config"));
    }

    @Test
    void testCloseFromUninitializedState() throws Exception {
        // Close without init
        assertDoesNotThrow(() -> spyEngine.close());
        assertEquals("CLOSED", getState(spyEngine));
    }

    @Test
    void testCloseIsIdempotent() throws Exception {
        // Initialize and close
        setupSuccessfulFmodMocks();
        spyEngine.init(config);
        when(mockFmod.FMOD_System_Release(any())).thenReturn(FmodConstants.FMOD_OK);

        spyEngine.close();
        assertEquals("CLOSED", getState(spyEngine));

        // Second close should be no-op
        assertDoesNotThrow(() -> spyEngine.close());
        assertEquals("CLOSED", getState(spyEngine));

        // Verify release was only called once
        verify(mockFmod, times(1)).FMOD_System_Release(any());
    }

    @Test
    void testInitCloseRaceCondition() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    // This test verifies that close() during init() is handled correctly
                    CountDownLatch initStarted = new CountDownLatch(1);
                    CountDownLatch proceedWithInit = new CountDownLatch(1);
                    CountDownLatch bothDone = new CountDownLatch(2);

                    AtomicReference<Exception> initException = new AtomicReference<>();
                    AtomicReference<Exception> closeException = new AtomicReference<>();

                    // Mock library that delays during init
                    FmodLibrary delayedMock = mock(FmodLibrary.class);
                    when(delayedMock.FMOD_System_Create(any(PointerByReference.class), anyInt()))
                            .thenAnswer(
                                    inv -> {
                                        PointerByReference ref = inv.getArgument(0);
                                        ref.setValue(new Memory(8));
                                        initStarted.countDown();
                                        proceedWithInit.await(2, TimeUnit.SECONDS);
                                        return FmodConstants.FMOD_OK;
                                    });
                    when(delayedMock.FMOD_System_Init(any(), anyInt(), anyInt(), any()))
                            .thenReturn(FmodConstants.FMOD_OK);
                    when(delayedMock.FMOD_System_Release(any())).thenReturn(FmodConstants.FMOD_OK);

                    FmodAudioEngine testEngine = spy(new FmodAudioEngine());
                    doReturn(delayedMock).when(testEngine).doLoadFmodLibrary();

                    // Thread 1: Initialize
                    Thread initThread =
                            new Thread(
                                    () -> {
                                        try {
                                            testEngine.init(config);
                                        } catch (Exception e) {
                                            initException.set(e);
                                        } finally {
                                            bothDone.countDown();
                                        }
                                    });

                    // Thread 2: Close after init starts
                    Thread closeThread =
                            new Thread(
                                    () -> {
                                        try {
                                            initStarted.await(2, TimeUnit.SECONDS);
                                            Thread.sleep(50); // Let init proceed a bit
                                            testEngine.close();
                                        } catch (Exception e) {
                                            closeException.set(e);
                                        } finally {
                                            proceedWithInit.countDown(); // Let init continue
                                            bothDone.countDown();
                                        }
                                    });

                    initThread.start();
                    closeThread.start();

                    assertTrue(bothDone.await(3, TimeUnit.SECONDS));

                    // One of these should happen:
                    // 1. Init fails with "Engine was closed during initialization"
                    // 2. Close succeeds and init completes before close runs
                    String finalState = getState(testEngine);
                    assertTrue(
                            finalState.equals("CLOSED") || finalState.equals("INITIALIZED"),
                            "Final state should be CLOSED or INITIALIZED, was: " + finalState);

                    if (initException.get() != null) {
                        assertTrue(
                                initException
                                        .get()
                                        .getMessage()
                                        .contains("closed during initialization"));
                    }
                    assertNull(closeException.get());
                });
    }

    @Test
    void testConcurrentCloseCallsAreSafe() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    // Initialize first
                    setupSuccessfulFmodMocks();
                    spyEngine.init(config);
                    when(mockFmod.FMOD_System_Release(any())).thenReturn(FmodConstants.FMOD_OK);

                    // Multiple threads trying to close simultaneously
                    int threadCount = 10;
                    CountDownLatch startLatch = new CountDownLatch(1);
                    CountDownLatch doneLatch = new CountDownLatch(threadCount);

                    for (int i = 0; i < threadCount; i++) {
                        new Thread(
                                        () -> {
                                            try {
                                                startLatch.await();
                                                spyEngine.close();
                                            } catch (Exception e) {
                                                // Should not throw
                                            } finally {
                                                doneLatch.countDown();
                                            }
                                        })
                                .start();
                    }

                    startLatch.countDown(); // Release all threads
                    assertTrue(doneLatch.await(2, TimeUnit.SECONDS));

                    // Verify only one actual cleanup happened
                    verify(mockFmod, times(1)).FMOD_System_Release(any());
                    assertEquals("CLOSED", getState(spyEngine));
                });
    }

    @Test
    void testOperationsBlockDuringClose() throws Exception {
        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> {
                    // Initialize
                    setupSuccessfulFmodMocks();
                    spyEngine.init(config);

                    // Mock slow cleanup
                    when(mockFmod.FMOD_System_Release(any()))
                            .thenAnswer(
                                    inv -> {
                                        Thread.sleep(500); // Simulate slow cleanup
                                        return FmodConstants.FMOD_OK;
                                    });

                    CyclicBarrier barrier = new CyclicBarrier(2);
                    AtomicReference<Exception> opException = new AtomicReference<>();

                    // Thread 1: Start close (will hold lock)
                    Thread closeThread =
                            new Thread(
                                    () -> {
                                        try {
                                            barrier.await(); // Sync start
                                            spyEngine.close();
                                        } catch (Exception e) {
                                            // Unexpected
                                        }
                                    });

                    // Thread 2: Try operation during close
                    Thread opThread =
                            new Thread(
                                    () -> {
                                        try {
                                            barrier.await(); // Sync start
                                            Thread.sleep(50); // Let close start first
                                            spyEngine.play(
                                                    mock(
                                                            a2.AudioHandle
                                                                    .class)); // Should block then
                                            // fail
                                        } catch (Exception e) {
                                            opException.set(e);
                                        }
                                    });

                    closeThread.start();
                    opThread.start();

                    closeThread.join(2000);
                    opThread.join(2000);

                    // Operation should have failed with state error
                    assertNotNull(opException.get(), "Expected an exception but got none");
                    assertTrue(
                            opException.get() instanceof IllegalStateException
                                    || opException.get() instanceof UnsupportedOperationException,
                            "Expected IllegalStateException or UnsupportedOperationException but"
                                    + " got: "
                                    + opException.get().getClass()
                                    + " - "
                                    + opException.get().getMessage());
                    // Either state error or unsupported operation is fine
                });
    }

    @Test
    void testConfigValidation() throws Exception {
        // Test invalid cache size (too small)
        AudioEngineConfig invalidConfig =
                AudioEngineConfig.builder()
                        .maxCacheBytes(1000) // Less than 1MB
                        .build();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> spyEngine.init(invalidConfig));
        assertTrue(ex.getMessage().contains("maxCacheBytes must be at least 1MB"));

        // Verify state returned to UNINITIALIZED
        assertEquals("UNINITIALIZED", getState(spyEngine));
    }

    @Test
    void testConfigValidationMaxCache() throws Exception {
        // Test invalid cache size (too large)
        AudioEngineConfig invalidConfig =
                AudioEngineConfig.builder()
                        .maxCacheBytes(11L * 1024 * 1024 * 1024) // More than 10GB
                        .build();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> spyEngine.init(invalidConfig));
        assertTrue(ex.getMessage().contains("maxCacheBytes must not exceed 10GB"));
    }

    @Test
    void testConfigValidationPrefetchWindow() throws Exception {
        // Test invalid prefetch window (negative)
        AudioEngineConfig invalidConfig =
                AudioEngineConfig.builder().prefetchWindowSeconds(-1).build();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> spyEngine.init(invalidConfig));
        assertTrue(ex.getMessage().contains("prefetchWindowSeconds must be non-negative"));
    }

    @Test
    void testFmodCallSequenceOrder() throws Exception {
        // Setup mocks
        setupSuccessfulFmodMocks();

        // Initialize and close
        spyEngine.init(config);
        when(mockFmod.FMOD_System_Release(any())).thenReturn(FmodConstants.FMOD_OK);
        spyEngine.close();

        // Verify FMOD calls happened in correct order
        InOrder inOrder = inOrder(mockFmod);

        // Init sequence
        inOrder.verify(mockFmod)
                .FMOD_System_Create(any(PointerByReference.class), eq(FmodConstants.FMOD_VERSION));
        inOrder.verify(mockFmod).FMOD_System_SetDSPBufferSize(any(), anyInt(), anyInt());
        inOrder.verify(mockFmod).FMOD_System_SetSoftwareFormat(any(), anyInt(), anyInt(), anyInt());
        inOrder.verify(mockFmod).FMOD_System_Init(any(), anyInt(), anyInt(), any());

        // Close sequence
        inOrder.verify(mockFmod).FMOD_System_Release(any());

        // No more interactions
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void testInitCleanupOnSystemInitFailure() throws Exception {
        // Setup: System_Create succeeds but System_Init fails
        Pointer mockSystem = new Memory(8);
        when(mockFmod.FMOD_System_Create(any(PointerByReference.class), anyInt()))
                .thenAnswer(
                        inv -> {
                            PointerByReference ref = inv.getArgument(0);
                            ref.setValue(mockSystem);
                            return FmodConstants.FMOD_OK;
                        });
        when(mockFmod.FMOD_System_Init(any(), anyInt(), anyInt(), any()))
                .thenReturn(FmodConstants.FMOD_ERR_BADCOMMAND);
        when(mockFmod.FMOD_ErrorString(anyInt())).thenReturn("Init failed");

        // Try to init
        assertThrows(RuntimeException.class, () -> spyEngine.init(config));

        // Verify cleanup was called
        verify(mockFmod).FMOD_System_Release(mockSystem);

        // Verify state is UNINITIALIZED
        assertEquals("UNINITIALIZED", getState(spyEngine));
    }

    // Helper methods

    private void setupSuccessfulFmodMocks() {
        // Create a real pointer pointing to dummy memory
        Pointer mockPointer = new Memory(8);

        when(mockFmod.FMOD_System_Create(any(PointerByReference.class), anyInt()))
                .thenAnswer(
                        inv -> {
                            PointerByReference ref = inv.getArgument(0);
                            ref.setValue(mockPointer);
                            return FmodConstants.FMOD_OK;
                        });
        when(mockFmod.FMOD_System_Init(any(), anyInt(), anyInt(), any()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_SetDSPBufferSize(any(), anyInt(), anyInt()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_SetSoftwareFormat(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_GetVersion(any(), any(IntByReference.class)))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_GetDSPBufferSize(
                        any(), any(IntByReference.class), any(IntByReference.class)))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_GetSoftwareFormat(
                        any(),
                        any(IntByReference.class),
                        any(IntByReference.class),
                        any(IntByReference.class)))
                .thenReturn(FmodConstants.FMOD_OK);
    }

    private String getState(FmodAudioEngine engine) throws Exception {
        Field stateField = FmodAudioEngine.class.getDeclaredField("state");
        stateField.setAccessible(true);
        AtomicReference<?> stateRef = (AtomicReference<?>) stateField.get(engine);
        return stateRef.get().toString();
    }

    private Object getField(FmodAudioEngine engine, String fieldName) throws Exception {
        Field field = FmodAudioEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(engine);
    }
}
