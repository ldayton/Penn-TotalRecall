package audio.fmod;

import lombok.experimental.UtilityClass;

/**
 * FMOD Core API constants from fmod_common.h and fmod.h. Auto-generated from FMOD headers - DO NOT
 * EDIT MANUALLY. Generated from FMOD version 2.03.09
 */
@UtilityClass
class FmodConstants {

    // Version
    static final int FMOD_VERSION = 0x00020309; // 2.03.09

    // Result codes
    static final int FMOD_ERR_ALREADY_LOCKED = 78;
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
    static final int FMOD_ERR_EVENT_ALREADY_LOADED = 70;
    static final int FMOD_ERR_EVENT_LIVEUPDATE_BUSY = 71;
    static final int FMOD_ERR_EVENT_LIVEUPDATE_MISMATCH = 72;
    static final int FMOD_ERR_EVENT_LIVEUPDATE_TIMEOUT = 73;
    static final int FMOD_ERR_EVENT_NOTFOUND = 74;
    static final int FMOD_ERR_FILE_BAD = 13;
    static final int FMOD_ERR_FILE_COULDNOTSEEK = 14;
    static final int FMOD_ERR_FILE_DISKEJECTED = 15;
    static final int FMOD_ERR_FILE_ENDOFDATA = 17;
    static final int FMOD_ERR_FILE_EOF = 16;
    static final int FMOD_ERR_FILE_NOTFOUND = 18;
    static final int FMOD_ERR_FORMAT = 19;
    static final int FMOD_ERR_HEADER_MISMATCH = 20;
    static final int FMOD_ERR_HTTP = 21;
    static final int FMOD_ERR_HTTP_ACCESS = 22;
    static final int FMOD_ERR_HTTP_PROXY_AUTH = 23;
    static final int FMOD_ERR_HTTP_SERVER_ERROR = 24;
    static final int FMOD_ERR_HTTP_TIMEOUT = 25;
    static final int FMOD_ERR_INITIALIZATION = 26;
    static final int FMOD_ERR_INITIALIZED = 27;
    static final int FMOD_ERR_INTERNAL = 28;
    static final int FMOD_ERR_INVALID_FLOAT = 29;
    static final int FMOD_ERR_INVALID_HANDLE = 30;
    static final int FMOD_ERR_INVALID_PARAM = 31;
    static final int FMOD_ERR_INVALID_POSITION = 32;
    static final int FMOD_ERR_INVALID_SPEAKER = 33;
    static final int FMOD_ERR_INVALID_STRING = 77;
    static final int FMOD_ERR_INVALID_SYNCPOINT = 34;
    static final int FMOD_ERR_INVALID_THREAD = 35;
    static final int FMOD_ERR_INVALID_VECTOR = 36;
    static final int FMOD_ERR_MAXAUDIBLE = 37;
    static final int FMOD_ERR_MEMORY = 38;
    static final int FMOD_ERR_MEMORY_CANTPOINT = 39;
    static final int FMOD_ERR_NEEDS3D = 40;
    static final int FMOD_ERR_NEEDSHARDWARE = 41;
    static final int FMOD_ERR_NET_CONNECT = 42;
    static final int FMOD_ERR_NET_SOCKET_ERROR = 43;
    static final int FMOD_ERR_NET_URL = 44;
    static final int FMOD_ERR_NET_WOULD_BLOCK = 45;
    static final int FMOD_ERR_NOTREADY = 46;
    static final int FMOD_ERR_NOT_LOCKED = 79;
    static final int FMOD_ERR_OUTPUT_ALLOCATED = 47;
    static final int FMOD_ERR_OUTPUT_CREATEBUFFER = 48;
    static final int FMOD_ERR_OUTPUT_DRIVERCALL = 49;
    static final int FMOD_ERR_OUTPUT_FORMAT = 50;
    static final int FMOD_ERR_OUTPUT_INIT = 51;
    static final int FMOD_ERR_OUTPUT_NODRIVERS = 52;
    static final int FMOD_ERR_PLUGIN = 53;
    static final int FMOD_ERR_PLUGIN_MISSING = 54;
    static final int FMOD_ERR_PLUGIN_RESOURCE = 55;
    static final int FMOD_ERR_PLUGIN_VERSION = 56;
    static final int FMOD_ERR_RECORD = 57;
    static final int FMOD_ERR_RECORD_DISCONNECTED = 80;
    static final int FMOD_ERR_REVERB_CHANNELGROUP = 58;
    static final int FMOD_ERR_REVERB_INSTANCE = 59;
    static final int FMOD_ERR_STUDIO_NOT_LOADED = 76;
    static final int FMOD_ERR_STUDIO_UNINITIALIZED = 75;
    static final int FMOD_ERR_SUBSOUNDS = 60;
    static final int FMOD_ERR_SUBSOUND_ALLOCATED = 61;
    static final int FMOD_ERR_SUBSOUND_CANTMOVE = 62;
    static final int FMOD_ERR_TAGNOTFOUND = 63;
    static final int FMOD_ERR_TOOMANYCHANNELS = 64;
    static final int FMOD_ERR_TOOMANYSAMPLES = 81;
    static final int FMOD_ERR_TRUNCATED = 65;
    static final int FMOD_ERR_UNIMPLEMENTED = 66;
    static final int FMOD_ERR_UNINITIALIZED = 67;
    static final int FMOD_ERR_UNSUPPORTED = 68;
    static final int FMOD_ERR_VERSION = 69;
    static final int FMOD_OK = 0;

    // FMOD_INITFLAGS
    static final int FMOD_INIT_3D_RIGHTHANDED = 0x00000004;
    static final int FMOD_INIT_CHANNEL_DISTANCEFILTER = 0x00000200;
    static final int FMOD_INIT_CHANNEL_LOWPASS = 0x00000100;
    static final int FMOD_INIT_CLIP_OUTPUT = 0x00000008;
    static final int FMOD_INIT_GEOMETRY_USECLOSEST = 0x00040000;
    static final int FMOD_INIT_MEMORY_TRACKING = 0x00400000;
    static final int FMOD_INIT_MIX_FROM_UPDATE = 0x00000002;
    static final int FMOD_INIT_NORMAL = 0x00000000;
    static final int FMOD_INIT_PREFER_DOLBY_DOWNMIX = 0x00080000;
    static final int FMOD_INIT_PROFILE_ENABLE = 0x00010000;
    static final int FMOD_INIT_PROFILE_METER_ALL = 0x00200000;
    static final int FMOD_INIT_STREAM_FROM_UPDATE = 0x00000001;
    static final int FMOD_INIT_THREAD_UNSAFE = 0x00100000;
    static final int FMOD_INIT_VOL0_BECOMES_VIRTUAL = 0x00020000;

    // FMOD_MODE
    static final int FMOD_2D = 0x00000008;
    static final int FMOD_3D = 0x00000010;
    static final int FMOD_3D_CUSTOMROLLOFF = 0x04000000;
    static final int FMOD_3D_HEADRELATIVE = 0x00040000;
    static final int FMOD_3D_IGNOREGEOMETRY = 0x40000000;
    static final int FMOD_3D_INVERSEROLLOFF = 0x00100000;
    static final int FMOD_3D_INVERSETAPEREDROLLOFF = 0x00800000;
    static final int FMOD_3D_LINEARROLLOFF = 0x00200000;
    static final int FMOD_3D_LINEARSQUAREROLLOFF = 0x00400000;
    static final int FMOD_3D_WORLDRELATIVE = 0x00080000;
    static final int FMOD_ACCURATETIME = 0x00004000;
    static final int FMOD_CHANNELCONTROL_CHANNEL = 0x00000000;
    static final int FMOD_CHANNELCONTROL_CHANNELGROUP = 0x00000001;
    static final int FMOD_CHANNELCONTROL_MAX = 0x00000002;
    static final int FMOD_CREATECOMPRESSEDSAMPLE = 0x00000200;
    static final int FMOD_CREATESAMPLE = 0x00000100;
    static final int FMOD_CREATESTREAM = 0x00000080;
    static final int FMOD_DEFAULT = 0x00000000;
    static final int FMOD_IGNORETAGS = 0x02000000;
    static final int FMOD_LOOP_BIDI = 0x00000004;
    static final int FMOD_LOOP_NORMAL = 0x00000002;
    static final int FMOD_LOOP_OFF = 0x00000001;
    static final int FMOD_LOWMEM = 0x08000000;
    static final int FMOD_MPEGSEARCH = 0x00008000;
    static final int FMOD_NONBLOCKING = 0x00010000;
    static final int FMOD_OPENMEMORY = 0x00000800;
    static final int FMOD_OPENMEMORY_POINT = 0x10000000;
    static final int FMOD_OPENONLY = 0x00002000;
    static final int FMOD_OPENRAW = 0x00001000;
    static final int FMOD_OPENSTATE_BUFFERING = 0x00000004;
    static final int FMOD_OPENSTATE_CONNECTING = 0x00000003;
    static final int FMOD_OPENSTATE_ERROR = 0x00000002;
    static final int FMOD_OPENSTATE_LOADING = 0x00000001;
    static final int FMOD_OPENSTATE_MAX = 0x00000008;
    static final int FMOD_OPENSTATE_PLAYING = 0x00000006;
    static final int FMOD_OPENSTATE_READY = 0x00000000;
    static final int FMOD_OPENSTATE_SEEKING = 0x00000005;
    static final int FMOD_OPENSTATE_SETPOSITION = 0x00000007;
    static final int FMOD_OPENUSER = 0x00000400;
    static final int FMOD_SOUNDGROUP_BEHAVIOR_FAIL = 0x00000000;
    static final int FMOD_SOUNDGROUP_BEHAVIOR_MAX = 0x00000003;
    static final int FMOD_SOUNDGROUP_BEHAVIOR_MUTE = 0x00000001;
    static final int FMOD_SOUNDGROUP_BEHAVIOR_STEALLOWEST = 0x00000002;
    static final int FMOD_SOUND_FORMAT_BITSTREAM = 0x00000006;
    static final int FMOD_SOUND_FORMAT_MAX = 0x00000007;
    static final int FMOD_SOUND_FORMAT_NONE = 0x00000000;
    static final int FMOD_SOUND_FORMAT_PCM16 = 0x00000002;
    static final int FMOD_SOUND_FORMAT_PCM24 = 0x00000003;
    static final int FMOD_SOUND_FORMAT_PCM32 = 0x00000004;
    static final int FMOD_SOUND_FORMAT_PCM8 = 0x00000001;
    static final int FMOD_SOUND_FORMAT_PCMFLOAT = 0x00000005;
    static final int FMOD_THREAD_TYPE_CONVOLUTION1 = 0x0000000B;
    static final int FMOD_THREAD_TYPE_CONVOLUTION2 = 0x0000000C;
    static final int FMOD_THREAD_TYPE_FEEDER = 0x00000001;
    static final int FMOD_THREAD_TYPE_FILE = 0x00000003;
    static final int FMOD_THREAD_TYPE_GEOMETRY = 0x00000006;
    static final int FMOD_THREAD_TYPE_MAX = 0x0000000D;
    static final int FMOD_THREAD_TYPE_MIXER = 0x00000000;
    static final int FMOD_THREAD_TYPE_NONBLOCKING = 0x00000004;
    static final int FMOD_THREAD_TYPE_PROFILER = 0x00000007;
    static final int FMOD_THREAD_TYPE_RECORD = 0x00000005;
    static final int FMOD_THREAD_TYPE_STREAM = 0x00000002;
    static final int FMOD_THREAD_TYPE_STUDIO_LOAD_BANK = 0x00000009;
    static final int FMOD_THREAD_TYPE_STUDIO_LOAD_SAMPLE = 0x0000000A;
    static final int FMOD_THREAD_TYPE_STUDIO_UPDATE = 0x00000008;
    static final int FMOD_UNIQUE = 0x00020000;
    static final int FMOD_VIRTUAL_PLAYFROMSTART = 0x80000000;

    // FMOD_TIMEUNIT
    static final int FMOD_TIMEUNIT_MODORDER = 0x00000100;
    static final int FMOD_TIMEUNIT_MODPATTERN = 0x00000400;
    static final int FMOD_TIMEUNIT_MODROW = 0x00000200;
    static final int FMOD_TIMEUNIT_MS = 0x00000001;
    static final int FMOD_TIMEUNIT_PCM = 0x00000002;
    static final int FMOD_TIMEUNIT_PCMBYTES = 0x00000004;
    static final int FMOD_TIMEUNIT_PCMFRACTION = 0x00000010;
    static final int FMOD_TIMEUNIT_RAWBYTES = 0x00000008;

    // FMOD_SYSTEM_CALLBACK_TYPE
    static final int FMOD_SYSTEM_CALLBACK_ALL = 0xFFFFFFFF;
    static final int FMOD_SYSTEM_CALLBACK_BADDSPCONNECTION = 0x00000010;
    static final int FMOD_SYSTEM_CALLBACK_BUFFEREDNOMIX = 0x00001000;
    static final int FMOD_SYSTEM_CALLBACK_DEVICELISTCHANGED = 0x00000001;
    static final int FMOD_SYSTEM_CALLBACK_DEVICELOST = 0x00000002;
    static final int FMOD_SYSTEM_CALLBACK_DEVICEREINITIALIZE = 0x00002000;
    static final int FMOD_SYSTEM_CALLBACK_ERROR = 0x00000080;
    static final int FMOD_SYSTEM_CALLBACK_MEMORYALLOCATIONFAILED = 0x00000004;
    static final int FMOD_SYSTEM_CALLBACK_OUTPUTUNDERRUN = 0x00004000;
    static final int FMOD_SYSTEM_CALLBACK_POSTMIX = 0x00000040;
    static final int FMOD_SYSTEM_CALLBACK_POSTUPDATE = 0x00000400;
    static final int FMOD_SYSTEM_CALLBACK_PREMIX = 0x00000020;
    static final int FMOD_SYSTEM_CALLBACK_PREUPDATE = 0x00000200;
    static final int FMOD_SYSTEM_CALLBACK_RECORDLISTCHANGED = 0x00000800;
    static final int FMOD_SYSTEM_CALLBACK_RECORDPOSITIONCHANGED = 0x00008000;
    static final int FMOD_SYSTEM_CALLBACK_THREADCREATED = 0x00000008;
    static final int FMOD_SYSTEM_CALLBACK_THREADDESTROYED = 0x00000100;

    // FMOD_CHANNELMASK
    static final int FMOD_CHANNELMASK_BACK_CENTER = 0x00000100;
    static final int FMOD_CHANNELMASK_BACK_LEFT = 0x00000040;
    static final int FMOD_CHANNELMASK_BACK_RIGHT = 0x00000080;
    static final int FMOD_CHANNELMASK_FRONT_CENTER = 0x00000004;
    static final int FMOD_CHANNELMASK_FRONT_LEFT = 0x00000001;
    static final int FMOD_CHANNELMASK_FRONT_RIGHT = 0x00000002;
    static final int FMOD_CHANNELMASK_LOW_FREQUENCY = 0x00000008;
    static final int FMOD_CHANNELMASK_SURROUND_LEFT = 0x00000010;
    static final int FMOD_CHANNELMASK_SURROUND_RIGHT = 0x00000020;

    // FMOD_OUTPUTTYPE
    static final int FMOD_OUTPUTTYPE_AAUDIO = 18;
    static final int FMOD_OUTPUTTYPE_ALSA = 9;
    static final int FMOD_OUTPUTTYPE_ASIO = 7;
    static final int FMOD_OUTPUTTYPE_AUDIO3D = 14;
    static final int FMOD_OUTPUTTYPE_AUDIOOUT = 13;
    static final int FMOD_OUTPUTTYPE_AUDIOTRACK = 11;
    static final int FMOD_OUTPUTTYPE_AUDIOWORKLET = 19;
    static final int FMOD_OUTPUTTYPE_AUTODETECT = 0;
    static final int FMOD_OUTPUTTYPE_COREAUDIO = 10;
    static final int FMOD_OUTPUTTYPE_MAX = 22;
    static final int FMOD_OUTPUTTYPE_NNAUDIO = 16;
    static final int FMOD_OUTPUTTYPE_NOSOUND = 2;
    static final int FMOD_OUTPUTTYPE_NOSOUND_NRT = 4;
    static final int FMOD_OUTPUTTYPE_OHAUDIO = 21;
    static final int FMOD_OUTPUTTYPE_OPENSL = 12;
    static final int FMOD_OUTPUTTYPE_PHASE = 20;
    static final int FMOD_OUTPUTTYPE_PULSEAUDIO = 8;
    static final int FMOD_OUTPUTTYPE_UNKNOWN = 1;
    static final int FMOD_OUTPUTTYPE_WASAPI = 6;
    static final int FMOD_OUTPUTTYPE_WAVWRITER = 3;
    static final int FMOD_OUTPUTTYPE_WAVWRITER_NRT = 5;
    static final int FMOD_OUTPUTTYPE_WEBAUDIO = 15;
    static final int FMOD_OUTPUTTYPE_WINSONIC = 17;

    // FMOD_SPEAKERMODE
    static final int FMOD_SPEAKERMODE_5POINT1 = 6;
    static final int FMOD_SPEAKERMODE_7POINT1 = 7;
    static final int FMOD_SPEAKERMODE_7POINT1POINT4 = 8;
    static final int FMOD_SPEAKERMODE_DEFAULT = 0;
    static final int FMOD_SPEAKERMODE_MAX = 9;
    static final int FMOD_SPEAKERMODE_MONO = 2;
    static final int FMOD_SPEAKERMODE_QUAD = 4;
    static final int FMOD_SPEAKERMODE_RAW = 1;
    static final int FMOD_SPEAKERMODE_STEREO = 3;
    static final int FMOD_SPEAKERMODE_SURROUND = 5;

    // FMOD_SOUND_TYPE
    static final int FMOD_SOUND_TYPE_AIFF = 1;
    static final int FMOD_SOUND_TYPE_ASF = 2;
    static final int FMOD_SOUND_TYPE_AT9 = 19;
    static final int FMOD_SOUND_TYPE_AUDIOQUEUE = 18;
    static final int FMOD_SOUND_TYPE_DLS = 3;
    static final int FMOD_SOUND_TYPE_FADPCM = 23;
    static final int FMOD_SOUND_TYPE_FLAC = 4;
    static final int FMOD_SOUND_TYPE_FSB = 5;
    static final int FMOD_SOUND_TYPE_IT = 6;
    static final int FMOD_SOUND_TYPE_MAX = 25;
    static final int FMOD_SOUND_TYPE_MEDIACODEC = 22;
    static final int FMOD_SOUND_TYPE_MEDIA_FOUNDATION = 21;
    static final int FMOD_SOUND_TYPE_MIDI = 7;
    static final int FMOD_SOUND_TYPE_MOD = 8;
    static final int FMOD_SOUND_TYPE_MPEG = 9;
    static final int FMOD_SOUND_TYPE_OGGVORBIS = 10;
    static final int FMOD_SOUND_TYPE_OPUS = 24;
    static final int FMOD_SOUND_TYPE_PLAYLIST = 11;
    static final int FMOD_SOUND_TYPE_RAW = 12;
    static final int FMOD_SOUND_TYPE_S3M = 13;
    static final int FMOD_SOUND_TYPE_UNKNOWN = 0;
    static final int FMOD_SOUND_TYPE_USER = 14;
    static final int FMOD_SOUND_TYPE_VORBIS = 20;
    static final int FMOD_SOUND_TYPE_WAV = 15;
    static final int FMOD_SOUND_TYPE_XM = 16;
    static final int FMOD_SOUND_TYPE_XMA = 17;
}
