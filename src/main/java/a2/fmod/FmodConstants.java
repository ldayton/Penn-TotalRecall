package a2.fmod;

import lombok.experimental.UtilityClass;

/** FMOD Core API constants from fmod_common.h and fmod.h. */
@UtilityClass
class FmodConstants {

    // FMOD Version
    static final int FMOD_VERSION = 0x00020309; // 2.03.09

    // Result codes
    static final int FMOD_OK = 0;
    static final int FMOD_ERR_BADCOMMAND = 1;
    static final int FMOD_ERR_CHANNEL_ALLOC = 2;
    static final int FMOD_ERR_CHANNEL_STOLEN = 3;
    static final int FMOD_ERR_DMA = 4;
    static final int FMOD_ERR_DSP_CONNECTION = 5;
    static final int FMOD_ERR_DSP_DONTPROCESS = 6;
    static final int FMOD_ERR_DSP_FORMAT = 7;
    static final int FMOD_ERR_DSP_INUSE = 8;
    static final int FMOD_ERR_DSP_NOTFOUND = 9;
    static final int FMOD_ERR_DSP_RESERVED = 10;
    static final int FMOD_ERR_DSP_SILENCE = 11;
    static final int FMOD_ERR_DSP_TYPE = 12;
    static final int FMOD_ERR_FILE_BAD = 13;
    static final int FMOD_ERR_FILE_COULDNOTSEEK = 14;
    static final int FMOD_ERR_FILE_DISKEJECTED = 15;
    static final int FMOD_ERR_FILE_EOF = 16;
    static final int FMOD_ERR_FILE_ENDOFDATA = 17;
    static final int FMOD_ERR_FILE_NOTFOUND = 18;
    static final int FMOD_ERR_FORMAT = 19;
    static final int FMOD_ERR_HEADER_MISMATCH = 20;
    static final int FMOD_ERR_HTTP = 21;

    // FMOD_INITFLAGS
    static final int FMOD_INIT_NORMAL = 0x00000000;
    static final int FMOD_INIT_STREAM_FROM_UPDATE = 0x00000001;
    static final int FMOD_INIT_MIX_FROM_UPDATE = 0x00000002;
    static final int FMOD_INIT_3D_RIGHTHANDED = 0x00000004;

    // FMOD_MODE
    static final int FMOD_DEFAULT = 0x00000000;
    static final int FMOD_LOOP_OFF = 0x00000001;
    static final int FMOD_LOOP_NORMAL = 0x00000002;
    static final int FMOD_LOOP_BIDI = 0x00000004;
    static final int FMOD_2D = 0x00000008;
    static final int FMOD_3D = 0x00000010;
    static final int FMOD_CREATESTREAM = 0x00000080;
    static final int FMOD_CREATESAMPLE = 0x00000100;
    static final int FMOD_CREATECOMPRESSEDSAMPLE = 0x00000200;
    static final int FMOD_OPENUSER = 0x00000400;
    static final int FMOD_OPENMEMORY = 0x00000800;
    static final int FMOD_OPENMEMORY_POINT = 0x10000000;
    static final int FMOD_OPENRAW = 0x00001000;
    static final int FMOD_OPENONLY = 0x00002000;
    static final int FMOD_ACCURATETIME = 0x00004000;
    static final int FMOD_MPEGSEARCH = 0x00008000;
    static final int FMOD_NONBLOCKING = 0x00010000;
    static final int FMOD_UNIQUE = 0x00020000;
    static final int FMOD_3D_HEADRELATIVE = 0x00040000;
    static final int FMOD_3D_WORLDRELATIVE = 0x00080000;
    static final int FMOD_3D_INVERSEROLLOFF = 0x00100000;
    static final int FMOD_3D_LINEARROLLOFF = 0x00200000;
    static final int FMOD_3D_LINEARSQUAREROLLOFF = 0x00400000;
    static final int FMOD_3D_INVERSETAPEREDROLLOFF = 0x00800000;
    static final int FMOD_3D_CUSTOMROLLOFF = 0x04000000;
    static final int FMOD_3D_IGNOREGEOMETRY = 0x40000000;
    static final int FMOD_IGNORETAGS = 0x02000000;
    static final int FMOD_LOWMEM = 0x08000000;
    static final int FMOD_LOADSECONDARYRAM = 0x20000000;
    static final int FMOD_VIRTUAL_PLAYFROMSTART = 0x80000000;

    // FMOD_TIMEUNIT
    static final int FMOD_TIMEUNIT_MS = 0x00000001;
    static final int FMOD_TIMEUNIT_PCM = 0x00000002;
    static final int FMOD_TIMEUNIT_PCMBYTES = 0x00000004;
    static final int FMOD_TIMEUNIT_RAWBYTES = 0x00000008;
    static final int FMOD_TIMEUNIT_PCMFRACTION = 0x00000010;
    static final int FMOD_TIMEUNIT_MODORDER = 0x00000100;
    static final int FMOD_TIMEUNIT_MODROW = 0x00000200;
    static final int FMOD_TIMEUNIT_MODPATTERN = 0x00000400;

    // FMOD_SYSTEM_CALLBACK_TYPE
    static final int FMOD_SYSTEM_CALLBACK_DEVICELISTCHANGED = 0x00000001;
    static final int FMOD_SYSTEM_CALLBACK_DEVICELOST = 0x00000002;
    static final int FMOD_SYSTEM_CALLBACK_MEMORYALLOCATIONFAILED = 0x00000004;
    static final int FMOD_SYSTEM_CALLBACK_THREADCREATED = 0x00000008;
    static final int FMOD_SYSTEM_CALLBACK_BADDSPCONNECTION = 0x00000010;
    static final int FMOD_SYSTEM_CALLBACK_PREMIX = 0x00000020;
    static final int FMOD_SYSTEM_CALLBACK_POSTMIX = 0x00000040;
    static final int FMOD_SYSTEM_CALLBACK_ERROR = 0x00000080;
    static final int FMOD_SYSTEM_CALLBACK_MIDMIX = 0x00000100;
    static final int FMOD_SYSTEM_CALLBACK_THREADDESTROYED = 0x00000200;
    static final int FMOD_SYSTEM_CALLBACK_PREUPDATE = 0x00000400;
    static final int FMOD_SYSTEM_CALLBACK_POSTUPDATE = 0x00000800;
    static final int FMOD_SYSTEM_CALLBACK_RECORDLISTCHANGED = 0x00001000;
    static final int FMOD_SYSTEM_CALLBACK_ALL = 0xFFFFFFFF;

    // FMOD_CHANNELMASK
    static final int FMOD_CHANNELMASK_FRONT_LEFT = 0x00000001;
    static final int FMOD_CHANNELMASK_FRONT_RIGHT = 0x00000002;
    static final int FMOD_CHANNELMASK_FRONT_CENTER = 0x00000004;
    static final int FMOD_CHANNELMASK_LOW_FREQUENCY = 0x00000008;
    static final int FMOD_CHANNELMASK_SURROUND_LEFT = 0x00000010;
    static final int FMOD_CHANNELMASK_SURROUND_RIGHT = 0x00000020;
    static final int FMOD_CHANNELMASK_BACK_LEFT = 0x00000040;
    static final int FMOD_CHANNELMASK_BACK_RIGHT = 0x00000080;

    // FMOD_OUTPUTTYPE
    static final int FMOD_OUTPUTTYPE_AUTODETECT = 0;
    static final int FMOD_OUTPUTTYPE_UNKNOWN = 1;
    static final int FMOD_OUTPUTTYPE_NOSOUND = 2;
    static final int FMOD_OUTPUTTYPE_WAVWRITER = 3;
    static final int FMOD_OUTPUTTYPE_NOSOUND_NRT = 4;
    static final int FMOD_OUTPUTTYPE_WAVWRITER_NRT = 5;
    static final int FMOD_OUTPUTTYPE_WASAPI = 6;
    static final int FMOD_OUTPUTTYPE_ASIO = 7;
    static final int FMOD_OUTPUTTYPE_PULSEAUDIO = 8;
    static final int FMOD_OUTPUTTYPE_ALSA = 9;
    static final int FMOD_OUTPUTTYPE_COREAUDIO = 10;
    static final int FMOD_OUTPUTTYPE_AUDIOTRACK = 11;
    static final int FMOD_OUTPUTTYPE_OPENSL = 12;
    static final int FMOD_OUTPUTTYPE_AUDIOOUT = 13;
    static final int FMOD_OUTPUTTYPE_AUDIO3D = 14;
    static final int FMOD_OUTPUTTYPE_WEBAUDIO = 15;
    static final int FMOD_OUTPUTTYPE_NNAUDIO = 16;
    static final int FMOD_OUTPUTTYPE_WINSONIC = 17;
    static final int FMOD_OUTPUTTYPE_AAUDIO = 18;
    static final int FMOD_OUTPUTTYPE_AUDIOWORKLET = 19;
    static final int FMOD_OUTPUTTYPE_PHASE = 20;
    static final int FMOD_OUTPUTTYPE_OHAUDIO = 21;

    // FMOD_SPEAKERMODE
    static final int FMOD_SPEAKERMODE_DEFAULT = 0;
    static final int FMOD_SPEAKERMODE_RAW = 1;
    static final int FMOD_SPEAKERMODE_MONO = 2;
    static final int FMOD_SPEAKERMODE_STEREO = 3;
    static final int FMOD_SPEAKERMODE_QUAD = 4;
    static final int FMOD_SPEAKERMODE_SURROUND = 5;
    static final int FMOD_SPEAKERMODE_5POINT1 = 6;
    static final int FMOD_SPEAKERMODE_7POINT1 = 7;
    static final int FMOD_SPEAKERMODE_7POINT1POINT4 = 8;

    // FMOD_SOUND_TYPE
    static final int FMOD_SOUND_TYPE_UNKNOWN = 0;
    static final int FMOD_SOUND_TYPE_AIFF = 1;
    static final int FMOD_SOUND_TYPE_ASF = 2;
    static final int FMOD_SOUND_TYPE_DLS = 3;
    static final int FMOD_SOUND_TYPE_FLAC = 4;
    static final int FMOD_SOUND_TYPE_FSB = 5;
    static final int FMOD_SOUND_TYPE_IT = 6;
    static final int FMOD_SOUND_TYPE_MIDI = 7;
    static final int FMOD_SOUND_TYPE_MOD = 8;
    static final int FMOD_SOUND_TYPE_MPEG = 9;
    static final int FMOD_SOUND_TYPE_OGGVORBIS = 10;
    static final int FMOD_SOUND_TYPE_PLAYLIST = 11;
    static final int FMOD_SOUND_TYPE_RAW = 12;
    static final int FMOD_SOUND_TYPE_S3M = 13;
    static final int FMOD_SOUND_TYPE_USER = 14;
    static final int FMOD_SOUND_TYPE_WAV = 15;
    static final int FMOD_SOUND_TYPE_XM = 16;
    static final int FMOD_SOUND_TYPE_XMA = 17;
    static final int FMOD_SOUND_TYPE_AUDIOQUEUE = 18;
    static final int FMOD_SOUND_TYPE_AT9 = 19;
    static final int FMOD_SOUND_TYPE_VORBIS = 20;
    static final int FMOD_SOUND_TYPE_MEDIA_FOUNDATION = 21;
    static final int FMOD_SOUND_TYPE_MEDIACODEC = 22;
    static final int FMOD_SOUND_TYPE_FADPCM = 23;
    static final int FMOD_SOUND_TYPE_OPUS = 24;
}
