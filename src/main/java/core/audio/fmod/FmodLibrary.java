package audio.fmod;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/** FMOD Core API interface via JNA. */
public interface FmodLibrary extends Library {
    // System creation and initialization
    int FMOD_System_Create(PointerByReference system, int headerversion);

    int FMOD_System_Init(Pointer system, int maxchannels, int flags, Pointer extradriverdata);

    int FMOD_System_Release(Pointer system);

    int FMOD_System_Update(Pointer system);

    int FMOD_System_SetOutput(Pointer system, int output);

    int FMOD_System_GetOutput(Pointer system, IntByReference output);

    int FMOD_System_SetSpeakerPosition(
            Pointer system, int speaker, float x, float y, boolean active);

    int FMOD_System_SetDSPBufferSize(Pointer system, int bufferlength, int numbuffers);

    int FMOD_System_GetDSPBufferSize(
            Pointer system, IntByReference bufferlength, IntByReference numbuffers);

    int FMOD_System_SetSoftwareFormat(
            Pointer system, int samplerate, int speakermode, int numrawspeakers);

    int FMOD_System_GetSoftwareFormat(
            Pointer system,
            IntByReference samplerate,
            IntByReference speakermode,
            IntByReference numrawspeakers);

    int FMOD_System_GetVersion(Pointer system, IntByReference version, IntByReference buildnumber);

    int FMOD_System_GetDriverInfo(
            Pointer system,
            int id,
            byte[] name,
            int namelen,
            byte[] guid,
            IntByReference systemrate,
            IntByReference speakermode,
            IntByReference channels);

    // Sound creation and management
    int FMOD_System_CreateSound(
            Pointer system,
            String name_or_data,
            int mode,
            Pointer exinfo,
            PointerByReference sound);

    int FMOD_System_CreateStream(
            Pointer system,
            String name_or_data,
            int mode,
            Pointer exinfo,
            PointerByReference sound);

    int FMOD_Sound_Release(Pointer sound);

    int FMOD_Sound_GetLength(Pointer sound, IntByReference length, int lengthtype);

    int FMOD_Sound_GetFormat(
            Pointer sound,
            IntByReference type,
            IntByReference format,
            IntByReference channels,
            IntByReference bits);

    int FMOD_Sound_GetDefaults(Pointer sound, FloatByReference frequency, IntByReference priority);

    int FMOD_Sound_SetMode(Pointer sound, int mode);

    int FMOD_Sound_GetMode(Pointer sound, IntByReference mode);

    // Channel and playback control
    int FMOD_System_PlaySound(
            Pointer system,
            Pointer sound,
            Pointer channelgroup,
            boolean paused,
            PointerByReference channel);

    int FMOD_Channel_Stop(Pointer channel);

    int FMOD_Channel_SetPaused(Pointer channel, boolean paused);

    int FMOD_Channel_GetPaused(Pointer channel, IntByReference paused);

    int FMOD_Channel_SetPosition(Pointer channel, int position, int postype);

    int FMOD_Channel_GetPosition(Pointer channel, IntByReference position, int postype);

    int FMOD_Channel_IsPlaying(Pointer channel, IntByReference isplaying);

    int FMOD_Channel_SetLoopPoints(
            Pointer channel, int loopstart, int loopstarttype, int loopend, int loopendtype);

    int FMOD_Channel_SetLoopCount(Pointer channel, int loopcount);

    int FMOD_Channel_SetVolume(Pointer channel, float volume);

    int FMOD_Channel_GetVolume(Pointer channel, FloatByReference volume);

    // Sound data reading
    int FMOD_Sound_ReadData(Pointer sound, Pointer buffer, int lenbytes, IntByReference read);

    int FMOD_Sound_SeekData(Pointer sound, int pcmoffset);

    int FMOD_Sound_Lock(
            Pointer sound,
            int offset,
            int length,
            PointerByReference ptr1,
            PointerByReference ptr2,
            IntByReference len1,
            IntByReference len2);

    int FMOD_Sound_Unlock(Pointer sound, Pointer ptr1, Pointer ptr2, int len1, int len2);

    // Note: FMOD_ErrorString is a static inline function in the header,
    // not an exported library symbol, so it cannot be called via JNA.
    // Error codes must be translated manually or via a lookup table.
}
