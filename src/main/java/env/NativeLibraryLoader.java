package env;

/**
 * Interface for loading native libraries via JNA.
 *
 * <p>This interface abstracts the native library loading mechanism to enable testing
 * with mocked implementations. In production, this wraps JNA's Native.loadLibrary(),
 * while in tests it can be mocked to avoid requiring actual native libraries.
 */
public interface NativeLibraryLoader {

    /**
     * Loads a native library using the specified library name and interface class.
     *
     * @param <T> the library interface type
     * @param libraryName the name of the library to load
     * @param interfaceClass the JNA interface class
     * @return the loaded library instance
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    <T> T loadLibrary(String libraryName, Class<T> interfaceClass);
}