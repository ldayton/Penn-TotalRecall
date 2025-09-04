package a2.fmod;

import a2.AudioBuffer;
import a2.exceptions.AudioEngineException;
import app.annotations.ThreadSafe;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles reading audio samples from FMOD sounds. Creates temporary stream sounds for efficient
 * sequential reading of PCM data.
 */
@ThreadSafe
@Slf4j
class FmodSampleReader {

    private final FmodLibrary fmod;
    private final Pointer system;

    FmodSampleReader(@NonNull FmodLibrary fmod, @NonNull Pointer system) {
        this.fmod = fmod;
        this.system = system;
    }

    /**
     * Read audio samples from a file at the specified frame range.
     *
     * @param filePath Path to the audio file
     * @param startFrame Starting frame position (0-based)
     * @param frameCount Number of frames to read
     * @return AudioBuffer containing the samples
     * @throws AudioEngineException if reading fails
     */
    AudioBuffer readSamples(@NonNull String filePath, long startFrame, long frameCount) {
        // Validate inputs
        if (startFrame < 0) {
            throw new AudioEngineException("Start frame cannot be negative: " + startFrame);
        }
        if (frameCount < 0) {
            throw new AudioEngineException("Frame count cannot be negative: " + frameCount);
        }

        Pointer tempSound = null;
        try {
            // Create sound in stream mode for reading
            PointerByReference soundRef = new PointerByReference();
            int flags = FmodConstants.FMOD_CREATESTREAM | FmodConstants.FMOD_OPENONLY;
            int result = fmod.FMOD_System_CreateSound(system, filePath, flags, null, soundRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to create stream for reading: " + "error code: " + result);
            }
            tempSound = soundRef.getValue();

            // Get sound format info
            IntByReference channelsRef = new IntByReference();
            IntByReference bitsRef = new IntByReference();
            FloatByReference frequencyRef = new FloatByReference();
            IntByReference lengthRef = new IntByReference();

            result = fmod.FMOD_Sound_GetFormat(tempSound, null, null, channelsRef, bitsRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to get sound format: " + "error code: " + result);
            }

            result = fmod.FMOD_Sound_GetDefaults(tempSound, frequencyRef, null);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to get sample rate: " + "error code: " + result);
            }

            result =
                    fmod.FMOD_Sound_GetLength(
                            tempSound, lengthRef, FmodConstants.FMOD_TIMEUNIT_PCM);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to get sound length: " + "error code: " + result);
            }

            int sampleRate = Math.round(frequencyRef.getValue());
            int channelCount = channelsRef.getValue();
            int bitsPerSample = bitsRef.getValue();
            int bytesPerSample = bitsPerSample / 8;
            int totalFrames = lengthRef.getValue();

            // Clamp frame count to available frames
            long actualFrameCount = Math.min(frameCount, totalFrames - startFrame);
            if (actualFrameCount <= 0) {
                // Return empty buffer if start is past end
                return new FmodAudioBuffer(new double[0], sampleRate, channelCount, startFrame, 0);
            }

            // Calculate bytes to read
            int samplesPerFrame = channelCount;
            int bytesPerFrame = samplesPerFrame * bytesPerSample;
            int totalBytesToRead = (int) (actualFrameCount * bytesPerFrame);

            // Seek to start position
            if (startFrame > 0) {
                result = fmod.FMOD_Sound_SeekData(tempSound, (int) startFrame);
                if (result != FmodConstants.FMOD_OK) {
                    throw new AudioEngineException(
                            "Failed to seek to frame " + startFrame + ": error code: " + result);
                }
            }

            // Read PCM data
            Memory buffer = new Memory(totalBytesToRead);
            IntByReference bytesReadRef = new IntByReference();
            result = fmod.FMOD_Sound_ReadData(tempSound, buffer, totalBytesToRead, bytesReadRef);
            if (result != FmodConstants.FMOD_OK) {
                throw new AudioEngineException(
                        "Failed to read audio data: " + "error code: " + result);
            }

            int actualBytesRead = bytesReadRef.getValue();
            int actualSamplesRead = actualBytesRead / bytesPerSample;

            // Convert to double array
            double[] samples =
                    convertPcmToDoubles(buffer, actualSamplesRead, actualBytesRead, bitsPerSample);

            long actualFramesRead = actualSamplesRead / channelCount;
            return new FmodAudioBuffer(
                    samples, sampleRate, channelCount, startFrame, actualFramesRead);

        } catch (Exception e) {
            if (e instanceof AudioEngineException) {
                throw (AudioEngineException) e;
            }
            throw new AudioEngineException("Failed to read samples: " + e.getMessage(), e);
        } finally {
            if (tempSound != null) {
                try {
                    int result = fmod.FMOD_Sound_Release(tempSound);
                    if (result != FmodConstants.FMOD_OK) {
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private double[] convertPcmToDoubles(
            Memory buffer, int actualSamplesRead, int actualBytesRead, int bitsPerSample) {
        double[] samples = new double[actualSamplesRead];

        if (bitsPerSample == 16) {
            short[] shortData = buffer.getShortArray(0, actualSamplesRead);
            for (int i = 0; i < actualSamplesRead; i++) {
                samples[i] = shortData[i] / 32767.0; // Convert to [-1, 1]
            }
        } else if (bitsPerSample == 24) {
            byte[] byteData = buffer.getByteArray(0, actualBytesRead);
            for (int i = 0, sampleIndex = 0; i < actualBytesRead; i += 3, sampleIndex++) {
                if (sampleIndex >= samples.length) break;
                // Little-endian 24-bit to int
                int sample24 =
                        ((byteData[i + 2] & 0xFF) << 16)
                                | ((byteData[i + 1] & 0xFF) << 8)
                                | (byteData[i] & 0xFF);
                // Sign extend
                if ((sample24 & 0x800000) != 0) {
                    sample24 |= 0xFF000000;
                }
                samples[sampleIndex] = sample24 / 8388607.0; // Convert to [-1, 1]
            }
        } else if (bitsPerSample == 32) {
            float[] floatData = buffer.getFloatArray(0, actualSamplesRead);
            for (int i = 0; i < actualSamplesRead; i++) {
                samples[i] = floatData[i]; // Already normalized
            }
        } else {
            throw new AudioEngineException("Unsupported bit depth: " + bitsPerSample);
        }

        return samples;
    }
}
