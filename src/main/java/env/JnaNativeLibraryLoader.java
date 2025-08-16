package env;

import com.sun.jna.Native;
import jakarta.inject.Singleton;

/**
 * Production implementation of NativeLibraryLoader that uses JNA's Native.loadLibrary().
 *
 * <p>This implementation provides the actual native library loading functionality
 * for production use. It directly delegates to JNA's Native.loadLibrary() method.
 */
@Singleton
public class JnaNativeLibraryLoader implements NativeLibraryLoader {

    @Override
    public <T> T loadLibrary(String libraryName, Class<T> interfaceClass) {
        return Native.loadLibrary(libraryName, interfaceClass);
    }
}