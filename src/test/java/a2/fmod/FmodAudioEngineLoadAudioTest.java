package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioEngineConfig;
import a2.AudioHandle;
import a2.exceptions.AudioLoadException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for FmodAudioEngine loadAudio() method. */
class FmodAudioEngineLoadAudioTest {

    private FmodAudioEngine engine;
    private AudioEngineConfig config;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new FmodAudioEngine();
        config =
                AudioEngineConfig.builder()
                        .engineType("fmod")
                        .mode(AudioEngineConfig.Mode.PLAYBACK)
                        .build();
        engine.init(config);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void testLoadValidFile() throws Exception {
        // Create a simple WAV file header (44 bytes)
        // This is a minimal valid WAV header for a 1-second 44.1kHz mono file
        byte[] wavHeader = createMinimalWavHeader();
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, wavHeader);

        // Load the file
        AudioHandle handle = engine.loadAudio(audioFile.toString());

        // Verify handle is valid
        assertNotNull(handle);
        assertTrue(handle.isValid());
        assertEquals(audioFile.toFile().getCanonicalPath(), handle.getFilePath());
    }

    @Test
    void testLoadNonExistentFile() {
        String nonExistentPath = "/does/not/exist/audio.wav";

        AudioLoadException ex =
                assertThrows(AudioLoadException.class, () -> engine.loadAudio(nonExistentPath));

        assertTrue(ex.getMessage().contains("Audio file not found"));
        assertTrue(ex.getMessage().contains(nonExistentPath));
    }

    @Test
    void testLoadSameFileTwice() throws Exception {
        // Create a valid WAV file
        byte[] wavHeader = createMinimalWavHeader();
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, wavHeader);

        // Load the file twice
        AudioHandle first = engine.loadAudio(audioFile.toString());
        AudioHandle second = engine.loadAudio(audioFile.toString());

        // Should return the same handle
        assertSame(first, second);
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void testLoadDifferentFile() throws Exception {
        // Create two valid WAV files
        byte[] wavHeader = createMinimalWavHeader();
        Path file1 = tempDir.resolve("file1.wav");
        Path file2 = tempDir.resolve("file2.wav");
        Files.write(file1, wavHeader);
        Files.write(file2, wavHeader);

        // Load first file
        AudioHandle handle1 = engine.loadAudio(file1.toString());
        assertNotNull(handle1);

        // Load second file
        AudioHandle handle2 = engine.loadAudio(file2.toString());
        assertNotNull(handle2);

        // Should be different handles
        assertNotSame(handle1, handle2);
        assertNotEquals(handle1.getId(), handle2.getId());

        // First handle should be invalidated
        assertFalse(handle1.isValid());
        assertTrue(handle2.isValid());
    }

    @Test
    void testLoadInvalidAudioFile() throws Exception {
        // Create a file with invalid content (not a valid audio file)
        Path invalidFile = tempDir.resolve("invalid.wav");
        Files.writeString(invalidFile, "This is not audio data");

        // Should throw an exception for invalid format
        assertThrows(Exception.class, () -> engine.loadAudio(invalidFile.toString()));
    }

    @Test
    void testLoadEmptyFile() throws Exception {
        // Create an empty file
        Path emptyFile = tempDir.resolve("empty.wav");
        Files.write(emptyFile, new byte[0]);

        // Should throw an exception for empty/invalid file
        assertThrows(Exception.class, () -> engine.loadAudio(emptyFile.toString()));
    }

    @Test
    void testPathNormalization() throws Exception {
        // Create a valid WAV file
        byte[] wavHeader = createMinimalWavHeader();
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, wavHeader);

        // Load with different path representations
        String absolutePath = audioFile.toString();
        String withDotPath = tempDir.resolve("./test.wav").toString();

        AudioHandle handle1 = engine.loadAudio(absolutePath);
        AudioHandle handle2 = engine.loadAudio(withDotPath);

        // Should recognize as the same file
        assertSame(handle1, handle2);
    }

    @Test
    void testLoadAfterClose() throws Exception {
        // Create a valid WAV file
        byte[] wavHeader = createMinimalWavHeader();
        Path audioFile = tempDir.resolve("test.wav");
        Files.write(audioFile, wavHeader);

        // Close the engine
        engine.close();

        // Try to load - should fail
        assertThrows(Exception.class, () -> engine.loadAudio(audioFile.toString()));
    }

    /**
     * Creates a minimal valid WAV file header. This is a 44-byte header for a 1-second, 44.1kHz,
     * 16-bit mono file.
     */
    private byte[] createMinimalWavHeader() {
        byte[] header = new byte[44];

        // RIFF header
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        // File size - 36 (36 + 8 = 44 bytes total)
        header[4] = 36;
        header[5] = 0;
        header[6] = 0;
        header[7] = 0;
        // WAVE header
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // fmt subchunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        // Subchunk size (16 for PCM)
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        // Audio format (1 = PCM)
        header[20] = 1;
        header[21] = 0;
        // Number of channels (1 = mono)
        header[22] = 1;
        header[23] = 0;
        // Sample rate (44100 Hz)
        header[24] = 0x44;
        header[25] = (byte) 0xAC;
        header[26] = 0;
        header[27] = 0;
        // Byte rate (44100 * 1 * 2 = 88200)
        header[28] = (byte) 0x88;
        header[29] = 0x58;
        header[30] = 0x01;
        header[31] = 0;
        // Block align (1 * 2 = 2)
        header[32] = 2;
        header[33] = 0;
        // Bits per sample (16)
        header[34] = 16;
        header[35] = 0;

        // data subchunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        // Data size (0 for empty audio)
        header[40] = 0;
        header[41] = 0;
        header[42] = 0;
        header[43] = 0;

        return header;
    }
}
