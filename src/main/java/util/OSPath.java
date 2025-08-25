package util;

/** Collection of static methods on file system paths, a la Python's os.path */
public class OsPath {

    /** Private constructor to prevent instantiation. */
    private OsPath() {}

    /**
     * Finds the basename of a path, defined as the path without its final dots and any follow
     * characters
     *
     * @param path Input path
     * @return The input path with the extension deleted
     */
    public static String basename(String path) {
        int i = path.lastIndexOf(".");
        if (i > 0) {
            return path.substring(0, i);
        }
        return path;
    }
}
