package a2.fmod;

import annotations.Audio;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

@Audio
public class FmodDebugTest {

    @Test
    void debugSampleMetadata() throws Exception {
        String filePath = Paths.get("packaging/samples/sample.wav").toAbsolutePath().toString();

        FmodLibraryLoader loader = new FmodLibraryLoader(new env.AppConfig(), new env.Platform());
        FmodLibrary fmod = loader.loadAudioLibrary(FmodLibrary.class);

        // Create system
        PointerByReference systemRef = new PointerByReference();
        int result = fmod.FMOD_System_Create(systemRef, FmodConstants.FMOD_VERSION);
        System.out.println("System create result: " + result);
        Pointer system = systemRef.getValue();

        // Initialize
        result = fmod.FMOD_System_Init(system, 256, FmodConstants.FMOD_INIT_NORMAL, null);
        System.out.println("System init result: " + result);

        // Try both CREATESTREAM and CREATESAMPLE
        for (int flags :
                new int[] {
                    FmodConstants.FMOD_CREATESTREAM | FmodConstants.FMOD_OPENONLY,
                    FmodConstants.FMOD_CREATESAMPLE | FmodConstants.FMOD_OPENONLY
                }) {

            String flagName =
                    (flags & FmodConstants.FMOD_CREATESTREAM) != 0
                            ? "CREATESTREAM"
                            : "CREATESAMPLE";
            System.out.println("\n=== Testing with " + flagName + " ===");

            PointerByReference soundRef = new PointerByReference();
            result = fmod.FMOD_System_CreateSound(system, filePath, flags, null, soundRef);
            System.out.println("CreateSound result: " + result);
            Pointer sound = soundRef.getValue();

            // Get length in different units
            IntByReference lengthRef = new IntByReference();

            result = fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
            System.out.println("Length (PCM samples): " + lengthRef.getValue());

            result =
                    fmod.FMOD_Sound_GetLength(
                            sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCMBYTES);
            System.out.println("Length (PCM bytes): " + lengthRef.getValue());

            result = fmod.FMOD_Sound_GetLength(sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_MS);
            System.out.println("Length (milliseconds): " + lengthRef.getValue());

            // Get format
            IntByReference channelsRef = new IntByReference();
            IntByReference bitsRef = new IntByReference();
            result = fmod.FMOD_Sound_GetFormat(sound, null, null, channelsRef, bitsRef);
            System.out.println("Channels: " + channelsRef.getValue());
            System.out.println("Bits per sample: " + bitsRef.getValue());

            // Get sample rate
            var frequencyRef = new com.sun.jna.ptr.FloatByReference();
            result = fmod.FMOD_Sound_GetDefaults(sound, frequencyRef, null);
            System.out.println("Sample rate: " + frequencyRef.getValue());

            // Calculate expected frames
            if (channelsRef.getValue() > 0 && bitsRef.getValue() > 0) {
                int bytesPerFrame = channelsRef.getValue() * (bitsRef.getValue() / 8);
                int pcmBytes = lengthRef.getValue();
                result =
                        fmod.FMOD_Sound_GetLength(
                                sound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCMBYTES);
                int calculatedFrames = lengthRef.getValue() / bytesPerFrame;
                System.out.println("Calculated frames from PCMBYTES: " + calculatedFrames);
            }

            // Release sound
            fmod.FMOD_Sound_Release(sound);
        }

        // Release system
        fmod.FMOD_System_Release(system);
    }
}
