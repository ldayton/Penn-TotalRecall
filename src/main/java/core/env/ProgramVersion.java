package core.env;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Scanner;
import lombok.NonNull;

/**
 * Service for managing program version information.
 *
 * <p>Provides version parsing, comparison, and validation functionality. Also implements Comparable
 * for version comparison operations.
 */
@Singleton
public class ProgramVersion implements Comparable<ProgramVersion> {

    private final String programVersion;
    private final String versionDelimiter;

    private int majorNumber;
    private int minorNumber;

    @Inject
    public ProgramVersion(AppConfig appConfig) {
        this.programVersion = appConfig.getProperty(AppConfig.APP_VERSION_KEY);
        this.versionDelimiter = "\\."; // Standard version delimiter
        parseVersion(programVersion);
    }

    /** Constructor for testing - allows injecting specific version configuration. */
    public ProgramVersion(@NonNull String programVersion, @NonNull String versionDelimiter) {
        this.programVersion = programVersion;
        this.versionDelimiter = versionDelimiter;
        parseVersion(programVersion);
    }

    private void parseVersion(String repr) {
        try (Scanner sc = new Scanner(repr).useDelimiter(versionDelimiter)) {
            majorNumber = -1;
            minorNumber = -1;
            if (sc.hasNextInt()) {
                majorNumber = sc.nextInt();
                if (sc.hasNextInt()) {
                    minorNumber = sc.nextInt();
                }
            }
        }
    }

    public int getMajorNumber() {
        return majorNumber;
    }

    public int getMinorNumber() {
        return minorNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ProgramVersion == false) {
            return false;
        }
        ProgramVersion otherVersion = (ProgramVersion) o;
        return otherVersion.getMajorNumber() == getMajorNumber()
                && otherVersion.getMinorNumber() == getMinorNumber();
    }

    @Override
    public int compareTo(ProgramVersion otherVersion) {
        if (this.equals(otherVersion)) {
            return 0;
        }
        if (otherVersion.getMajorNumber() > this.getMajorNumber()) {
            return -1;
        } else if (otherVersion.getMajorNumber() < this.getMajorNumber()) {
            return 1;
        } else if (otherVersion.getMinorNumber() > this.getMinorNumber()) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public int hashCode() {
        return Integer.toString(majorNumber).hashCode() + Integer.toString(minorNumber).hashCode();
    }

    /**
     * Gets the current program version.
     *
     * @return The current program version
     */
    public ProgramVersion getCurrentVersion() {
        return new ProgramVersion(programVersion, versionDelimiter);
    }

    /**
     * Creates a ProgramVersion from a version string representation.
     *
     * @param repr The version string to parse
     * @return A new ProgramVersion instance
     * @throws IllegalArgumentException if the version string is invalid
     */
    public ProgramVersion parseVersionString(@NonNull String repr) {
        if (validateVersionString(repr)) {
            return new ProgramVersion(repr, versionDelimiter);
        } else {
            throw new IllegalArgumentException("not a valid version string");
        }
    }

    /**
     * Validates if a version string is in the correct format.
     *
     * @param version The version string to validate
     * @return true if the version string is valid
     */
    public boolean validateVersionString(@NonNull String version) {
        try (Scanner sc = new Scanner(version).useDelimiter(versionDelimiter)) {
            if (sc.hasNextInt()) {
                sc.nextInt();
                if (sc.hasNextInt()) {
                    sc.nextInt();
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String toString() {
        return programVersion;
    }
}
