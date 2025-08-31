package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import a2.AudioEngineConfig;
import a2.AudioHandle;
import a2.exceptions.AudioLoadException;
import a2.exceptions.CorruptedAudioFileException;
import a2.exceptions.UnsupportedAudioFormatException;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** Tests for FmodAudioEngine loadAudio() method. */
class FmodAudioEngineLoadAudioTest {

    private FmodAudioEngine engine;
    private FmodAudioEngine spyEngine;
    private AudioEngineConfig config;
    private FmodLibrary mockFmod;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new FmodAudioEngine();
        config =
                AudioEngineConfig.builder()
                        .engineType("fmod")
                        .mode(AudioEngineConfig.Mode.PLAYBACK)
                        .build();
        mockFmod = mock(FmodLibrary.class);

        // Create spy that returns mock library
        spyEngine = spy(engine);
        doReturn(mockFmod).when(spyEngine).doLoadFmodLibrary();

        // Setup successful init mocks
        setupSuccessfulInit();
        spyEngine.init(config);
    }

    @Test
    void testLoadValidFile() throws Exception {
        // Create a real temp file
        Path audioFile = tempDir.resolve("test.wav");
        Files.writeString(audioFile, "fake audio data");

        // Setup FMOD mock to succeed
        Pointer mockSound = new Memory(8);
        setupSuccessfulCreateSound(mockSound);

        // Load the file
        AudioHandle handle = spyEngine.loadAudio(audioFile.toString());

        // Verify handle is valid
        assertNotNull(handle);
        assertTrue(handle.isValid());
        assertEquals(audioFile.toFile().getCanonicalPath(), handle.getFilePath());

        // Verify FMOD was called correctly
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockFmod)
                .FMOD_System_CreateSound(any(), pathCaptor.capture(), anyInt(), isNull(), any());
        assertEquals(audioFile.toFile().getCanonicalPath(), pathCaptor.getValue());
    }

    @Test
    void testLoadNonExistentFile() {
        String nonExistentPath = "/does/not/exist/audio.wav";

        AudioLoadException ex =
                assertThrows(AudioLoadException.class, () -> spyEngine.loadAudio(nonExistentPath));

        assertTrue(ex.getMessage().contains("Audio file not found"));
        assertTrue(ex.getMessage().contains(nonExistentPath));

        // Verify FMOD was never called
        verify(mockFmod, never()).FMOD_System_CreateSound(any(), any(), anyInt(), any(), any());
    }

    @Test
    void testLoadSameFileTwice() throws Exception {
        // Create a real temp file
        Path audioFile = tempDir.resolve("test.wav");
        Files.writeString(audioFile, "fake audio data");

        // Setup FMOD mock
        Pointer mockSound = new Memory(8);
        setupSuccessfulCreateSound(mockSound);

        // Load the file twice
        AudioHandle first = spyEngine.loadAudio(audioFile.toString());
        AudioHandle second = spyEngine.loadAudio(audioFile.toString());

        // Should return the same handle
        assertSame(first, second);
        assertEquals(first.getId(), second.getId());

        // FMOD should only be called once
        verify(mockFmod, times(1)).FMOD_System_CreateSound(any(), any(), anyInt(), any(), any());
        verify(mockFmod, never()).FMOD_Sound_Release(any());
    }

    @Test
    void testLoadDifferentFile() throws Exception {
        // Create two temp files
        Path file1 = tempDir.resolve("file1.wav");
        Path file2 = tempDir.resolve("file2.wav");
        Files.writeString(file1, "audio 1");
        Files.writeString(file2, "audio 2");

        // Setup FMOD mocks
        Pointer sound1 = new Memory(8);
        Pointer sound2 = new Memory(16);
        when(mockFmod.FMOD_System_CreateSound(any(), any(), anyInt(), any(), any()))
                .thenAnswer(
                        inv -> {
                            PointerByReference ref = inv.getArgument(4);
                            String path = inv.getArgument(1);
                            ref.setValue(path.contains("file1") ? sound1 : sound2);
                            return FmodConstants.FMOD_OK;
                        });
        when(mockFmod.FMOD_Sound_Release(any())).thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_Update(any())).thenReturn(FmodConstants.FMOD_OK);

        // Load first file
        AudioHandle handle1 = spyEngine.loadAudio(file1.toString());
        assertNotNull(handle1);

        // Load second file
        AudioHandle handle2 = spyEngine.loadAudio(file2.toString());
        assertNotNull(handle2);

        // Should be different handles
        assertNotSame(handle1, handle2);
        assertNotEquals(handle1.getId(), handle2.getId());

        // Verify first sound was released
        verify(mockFmod).FMOD_Sound_Release(sound1);
        verify(mockFmod).FMOD_System_Update(any());

        // Verify both sounds were created
        verify(mockFmod, times(2)).FMOD_System_CreateSound(any(), any(), anyInt(), any(), any());
    }

    @Test
    void testFmodErrorMapping() throws Exception {
        Path audioFile = tempDir.resolve("test.wav");
        Files.writeString(audioFile, "fake audio");

        // Test FILE_NOTFOUND error
        when(mockFmod.FMOD_System_CreateSound(any(), any(), anyInt(), any(), any()))
                .thenReturn(FmodConstants.FMOD_ERR_FILE_NOTFOUND);
        when(mockFmod.FMOD_ErrorString(anyInt())).thenReturn("File not found");

        assertThrows(AudioLoadException.class, () -> spyEngine.loadAudio(audioFile.toString()));

        // Test FORMAT error
        when(mockFmod.FMOD_System_CreateSound(any(), any(), anyInt(), any(), any()))
                .thenReturn(FmodConstants.FMOD_ERR_FORMAT);

        assertThrows(
                UnsupportedAudioFormatException.class,
                () -> spyEngine.loadAudio(audioFile.toString()));

        // Test FILE_BAD error
        when(mockFmod.FMOD_System_CreateSound(any(), any(), anyInt(), any(), any()))
                .thenReturn(FmodConstants.FMOD_ERR_FILE_BAD);

        assertThrows(
                CorruptedAudioFileException.class, () -> spyEngine.loadAudio(audioFile.toString()));
    }

    @Test
    void testResourceCleanup() throws Exception {
        // Create test files
        Path file1 = tempDir.resolve("file1.wav");
        Path file2 = tempDir.resolve("file2.wav");
        Files.writeString(file1, "audio 1");
        Files.writeString(file2, "audio 2");

        // Setup mocks
        Pointer sound1 = new Memory(8);
        Pointer sound2 = new Memory(16);
        AtomicInteger callCount = new AtomicInteger(0);
        when(mockFmod.FMOD_System_CreateSound(any(), any(), anyInt(), any(), any()))
                .thenAnswer(
                        inv -> {
                            PointerByReference ref = inv.getArgument(4);
                            ref.setValue(callCount.getAndIncrement() == 0 ? sound1 : sound2);
                            return FmodConstants.FMOD_OK;
                        });
        when(mockFmod.FMOD_Sound_Release(any())).thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_Update(any())).thenReturn(FmodConstants.FMOD_OK);

        // Load files
        spyEngine.loadAudio(file1.toString());
        spyEngine.loadAudio(file2.toString());

        // Verify proper cleanup sequence
        InOrder inOrder = inOrder(mockFmod);
        inOrder.verify(mockFmod)
                .FMOD_System_CreateSound(any(), contains("file1"), anyInt(), any(), any());
        inOrder.verify(mockFmod).FMOD_Sound_Release(sound1);
        inOrder.verify(mockFmod).FMOD_System_Update(any());
        inOrder.verify(mockFmod)
                .FMOD_System_CreateSound(any(), contains("file2"), anyInt(), any(), any());
    }

    @Test
    void testFmodFlagsUsed() throws Exception {
        Path audioFile = tempDir.resolve("test.wav");
        Files.writeString(audioFile, "audio");

        Pointer mockSound = new Memory(8);
        setupSuccessfulCreateSound(mockSound);

        spyEngine.loadAudio(audioFile.toString());

        // Verify correct FMOD flags were used
        ArgumentCaptor<Integer> modeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mockFmod).FMOD_System_CreateSound(any(), any(), modeCaptor.capture(), any(), any());

        int mode = modeCaptor.getValue();
        assertTrue((mode & FmodConstants.FMOD_CREATESTREAM) != 0, "Should use CREATESTREAM");
        assertTrue((mode & FmodConstants.FMOD_ACCURATETIME) != 0, "Should use ACCURATETIME");
    }

    // Helper methods

    private void setupSuccessfulInit() {
        Pointer mockSystem = new Memory(8);
        when(mockFmod.FMOD_System_Create(any(PointerByReference.class), anyInt()))
                .thenAnswer(
                        inv -> {
                            PointerByReference ref = inv.getArgument(0);
                            ref.setValue(mockSystem);
                            return FmodConstants.FMOD_OK;
                        });
        when(mockFmod.FMOD_System_Init(any(), anyInt(), anyInt(), any()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_SetDSPBufferSize(any(), anyInt(), anyInt()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_System_SetSoftwareFormat(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(FmodConstants.FMOD_OK);
        when(mockFmod.FMOD_ErrorString(anyInt())).thenReturn("Test error");
    }

    private void setupSuccessfulCreateSound(Pointer sound) {
        when(mockFmod.FMOD_System_CreateSound(any(), any(), anyInt(), any(), any()))
                .thenAnswer(
                        inv -> {
                            PointerByReference ref = inv.getArgument(4);
                            ref.setValue(sound);
                            return FmodConstants.FMOD_OK;
                        });
    }
}
