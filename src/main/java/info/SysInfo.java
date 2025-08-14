package info;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Collects system-specific information.
 *
 * <p>Values are determined only once, and then stored.
 */
public class SysInfo {

    public static final SysInfo sys = new SysInfo();

    public final boolean isMacOSX;
    public final boolean isWindowsAny;
    public final boolean isWindows7;

    public final int menuKey;
    public final int chunkSizeInSeconds;
    public final int maxInterpolatedPixels;

    public final double interplationToleratedErrorZoneInSec;

    public final boolean useAWTFileChoosers;
    public final boolean mouseMode;
    public final boolean forceListen;
    public final boolean bandpassFilter;
    public final boolean useAudioDataSmoothingForWaveform;
    public final boolean useWaveformImageDataSmoothing;
    public final boolean interpolateFrames;
    public final boolean nanoInterplation;
    public final boolean antiAliasWaveform;
    public final boolean doubleDraw;

    public final String aboutMessage;
    public final String userHomeDir;
    public final String updateAddress;
    public final String preferencesString;

    private SysInfo() {

        // determine current operating system
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName == null) {
            isMacOSX = isWindowsAny = isWindows7 = false;
        } else {
            if (osName.contains("windows 7")) {
                isWindowsAny = true;
                isWindows7 = true;
                isMacOSX = false;
            } else if (osName.contains("win")) {
                isWindowsAny = true;
                isWindows7 = false;
                isMacOSX = false;
            } else if (osName.contains("mac os x")) {
                isMacOSX = true;
                isWindowsAny = isWindows7 = false;
            } else {
                isWindowsAny = isWindows7 = isMacOSX = false;
            }
        }

        // workaround to possible openjdk bug in Graphics.drawImage()
        String vmName = System.getProperty("java.vm.name");
        doubleDraw = vmName != null && vmName.toLowerCase(Locale.ROOT).contains("openjdk");

        // what is the user's home directory?
        String homeVal = System.getProperty("user.home");
        if (homeVal == null) {
            String curDir = null;
            try {
                curDir = new File(".").getCanonicalPath();
            } catch (IOException e) {
                System.err.println("Failed to get canonical path: " + e.getMessage());
            }
            if (curDir == null) {
                userHomeDir = "";
            } else {
                userHomeDir = curDir;
            }
        } else {
            userHomeDir = homeVal;
        }

        // what is the correct update file location?
        if (isMacOSX) {
            updateAddress =
                    "http://memory.psych.upenn.edu/files/software/TotalRecall/version_files/mac_version.txt";
        } else {
            updateAddress =
                    "http://memory.psych.upenn.edu/files/software/TotalRecall/version_files/windows_version.txt";
        }

        // generate string displayed for "About this Program"
        aboutMessage =
                Constants.programName
                        + " v"
                        + Constants.programVersion
                        + "\n"
                        + "Maintainer: "
                        + Constants.maintainerEmail
                        + "\n\n"
                        + "Released by:"
                        + "\n"
                        + Constants.orgName
                        + "\n"
                        + Constants.orgAffiliationName
                        + "\n"
                        + Constants.orgHomepage
                        + "\n\n"
                        + "License: "
                        + Constants.license
                        + "\n"
                        + Constants.licenseSite;

        // modifier key for menu actions
        menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // customize appearance
        if (isMacOSX) {
            useAWTFileChoosers = true;
            preferencesString = "Preferences";
        } else {
            useAWTFileChoosers = false;
            preferencesString = isWindowsAny ? "Options" : "Preferences";
        }

        // audio settings
        if (isMacOSX) {
            interpolateFrames = true;
            maxInterpolatedPixels = 10;
            interplationToleratedErrorZoneInSec = 0.25;
            nanoInterplation = false;
        } else if (isWindowsAny) {
            interpolateFrames = true;
            maxInterpolatedPixels = 30;
            interplationToleratedErrorZoneInSec = 0.25;
            nanoInterplation = true;
        } else {
            interpolateFrames = true;
            maxInterpolatedPixels = Integer.MAX_VALUE;
            interplationToleratedErrorZoneInSec = 0.25;
            nanoInterplation = true;
        }

        // performance optimiziations
        chunkSizeInSeconds =
                (int)
                        Math.ceil(
                                Toolkit.getDefaultToolkit().getScreenSize().getWidth()
                                        / GUIConstants.zoomlessPixelsPerSecond);

        // annotation optimizations
        mouseMode = true;
        forceListen = false;

        // pretty waveform
        bandpassFilter = true; // essential for making words discernable
        useAudioDataSmoothingForWaveform = true; // essential for thickening the waveform
        useWaveformImageDataSmoothing = true; // prettier but blockier
        antiAliasWaveform = false; // no preference for it
    }
}
