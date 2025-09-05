package a2.fmod;

import a2.AudioData;
import annotations.Audio;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

@Audio
public class DebugReadTest {

    @Test
    void debugReadPastEof() throws Exception {
        FmodLibraryLoader loader = new FmodLibraryLoader(new env.AppConfig(), new env.Platform());

        FmodParallelSampleReader reader = new FmodParallelSampleReader(loader, 4);

        try {
            // File info
            var metadata = reader.getMetadata(Paths.get("packaging/samples/sample.wav")).get();
            System.out.println("File metadata:");
            System.out.println("  Total frames: " + metadata.frameCount());
            System.out.println("  Sample rate: " + metadata.sampleRate());
            System.out.println("  Channels: " + metadata.channelCount());

            // Try to read past EOF
            long startFrame = metadata.frameCount() - 100;
            long requestedFrames = 200;

            System.out.println(
                    "\nReading from frame "
                            + startFrame
                            + ", requesting "
                            + requestedFrames
                            + " frames");
            System.out.println(
                    "Expected to get: "
                            + Math.min(requestedFrames, metadata.frameCount() - startFrame)
                            + " frames");

            AudioData data =
                    reader.readSamples(
                                    Paths.get("packaging/samples/sample.wav"),
                                    startFrame,
                                    requestedFrames)
                            .get();

            System.out.println("\nActual result:");
            System.out.println("  Start frame: " + data.startFrame());
            System.out.println("  Frame count: " + data.frameCount());
            System.out.println("  Sample array length: " + data.samples().length);

        } finally {
            reader.close();
        }
    }
}
