import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Completely standalone program to test FMOD crash.
 */
public class FmodCrashDemo {
    
    // Constants
    private static final int FMOD_VERSION = 0x00020309;  // 2.03.09
    private static final int FMOD_INIT_NORMAL = 0x00000000;
    
    // Minimal FMOD interface
    public interface FmodLibrary extends Library {
        int FMOD_System_Create(PointerByReference system, int headerversion);
        int FMOD_System_Init(Pointer system, int maxchannels, int flags, Pointer extradriverdata);
        int FMOD_System_Release(Pointer system);
        // FIXED: GetVersion takes 3 params, not 2! (system, version, buildnumber)
        int FMOD_System_GetVersion(Pointer system, IntByReference version, IntByReference buildnumber);
    }
    
    public static void main(String[] args) {
        System.out.println("Testing with FmodLibrary2 (standalone)...");
        
        String fmodPath = FmodCrashDemo.class.getResource("/fmod/macos").getPath();
        System.out.println("FMOD path: " + fmodPath);
        NativeLibrary.addSearchPath("fmod", fmodPath);
        
        FmodLibrary fmod = Native.load("fmod", FmodLibrary.class);
        System.out.println("Library2 loaded: " + fmod);
        
        PointerByReference systemRef = new PointerByReference();
        int result = fmod.FMOD_System_Create(systemRef, FMOD_VERSION);
        System.out.println("System_Create result: " + result);
        
        Pointer system = systemRef.getValue();
        System.out.println("System pointer: " + system);
        
        result = fmod.FMOD_System_Init(system, 2, FMOD_INIT_NORMAL, null);
        System.out.println("System_Init result: " + result);
        
        IntByReference version = new IntByReference();
        IntByReference buildnumber = new IntByReference();  // Added missing parameter!
        System.out.println("About to call GetVersion with FmodLibrary2...");
        
        // Should not crash now with correct signature
        result = fmod.FMOD_System_GetVersion(system, version, buildnumber);
        
        System.out.println("GetVersion result: " + result);
        System.out.println("Version: 0x" + Integer.toHexString(version.getValue()));
        System.out.println("Build number: " + buildnumber.getValue());
        
        fmod.FMOD_System_Release(system);
        System.out.println("SUCCESS - FmodLibrary2 did not crash!");
    }
}