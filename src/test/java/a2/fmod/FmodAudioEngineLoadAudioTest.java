package a2.fmod;

import static org.junit.jupiter.api.Assertions.*;

import a2.AudioEngineConfig;
import a2.AudioHandle;
import a2.exceptions.AudioLoadException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        // Use the real sample.wav file from packaging/samples
        String samplePath = "packaging/samples/sample.wav";
        File sampleFile = new File(samplePath);
        assertTrue(sampleFile.exists(), "sample.wav should exist at " + samplePath);

        // Load the file
        AudioHandle handle = engine.loadAudio(sampleFile.getAbsolutePath());

        // Verify handle is valid
        assertNotNull(handle);
        assertTrue(handle.isValid());
        assertEquals(sampleFile.getCanonicalPath(), handle.getFilePath());
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
        // Use the real sample.wav file
        File sampleFile = new File("packaging/samples/sample.wav");
        assertTrue(sampleFile.exists());

        // Load the file twice
        AudioHandle first = engine.loadAudio(sampleFile.getAbsolutePath());
        AudioHandle second = engine.loadAudio(sampleFile.getAbsolutePath());

        // Should return the same handle
        assertSame(first, second);
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void testLoadDifferentFile() throws Exception {
        // Use the real sample files
        File file1 = new File("packaging/samples/sample.wav");
        File file2 = new File("packaging/samples/sweep.wav");
        assertTrue(file1.exists());
        assertTrue(file2.exists());

        // Load first file
        AudioHandle handle1 = engine.loadAudio(file1.getAbsolutePath());
        assertNotNull(handle1);

        // Load second file
        AudioHandle handle2 = engine.loadAudio(file2.getAbsolutePath());
        assertNotNull(handle2);

        // Should be different handles
        assertNotSame(handle1, handle2);
        assertNotEquals(handle1.getId(), handle2.getId());

        // Both handles should remain valid (loading a different file doesn't invalidate the first)
        assertTrue(handle1.isValid());
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
        // Copy sample.wav to temp directory for path normalization test
        Path sourceFile = Paths.get("packaging/samples/sample.wav");
        Path audioFile = tempDir.resolve("test.wav");
        Files.copy(sourceFile, audioFile);

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
        // Use the real sample.wav file
        File sampleFile = new File("packaging/samples/sample.wav");
        assertTrue(sampleFile.exists());

        // Close the engine
        engine.close();

        // Try to load - should fail
        assertThrows(Exception.class, () -> engine.loadAudio(sampleFile.getAbsolutePath()));
    }
}
