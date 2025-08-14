package util;

import info.Constants;
import info.SysInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Checks remote server (in a separate thread) for updates and informs user accordingly.
 *
 * <p>Notifications are handled through JOptionPane dialogs. A separate thread is used so network
 * problems won't hold up the event dispatch thread. This class is inherently thread-safe since it
 * does not change the state of other objects.
 */
public class CheckUpdatesThread implements Runnable {

    private boolean informEitherWay;

    /**
     * Creates a new update checking thread. User is always notified if updates are available.
     *
     * @param informEitherWay <tt>true</tt> if the user should be notified if updates are not
     *     available.
     */
    public CheckUpdatesThread(boolean informEitherWay) {
        this.informEitherWay = informEitherWay;
    }

    /**
     * Thread body.
     *
     * <p>Opens a connection to a remote server, parses an version file, and informs user if an
     * update is available.
     */
    @Override
    public void run() {
        //		System.out.println("-- Checking for Updates --");
        if (updateAvailable()) {
            informUpdate();
        } else {
            //			System.out.println("No update available\n");
            if (informEitherWay) {
                informNoUpdate();
            }
        }
    }

    /** Launches dialog to notify user an update is available. */
    private static void informUpdate() {
        String message =
                "An new version of "
                        + Constants.programName
                        + " is available!\n"
                        + "Please visit "
                        + Constants.downloadSite
                        + " to download.";
        GiveMessage.infoMessage(message);
    }

    /** Launches dialog to inform user no update is available. */
    private static void informNoUpdate() {
        GiveMessage.infoMessage(
                "You are running the latest version of " + Constants.programName + "!");
    }

    /**
     * Does the work of opening the connection and parsing the version file. Returns <tt>false</tt>
     * if there is a network error, handling exceptions internally.
     *
     * @return <tt>true</tt> if the version file indicates a more recent program version is
     *     available.
     */
    private static boolean updateAvailable() {
        URL url = null;
        try {
            url = new java.net.URI(SysInfo.sys.updateAddress).toURL();
            URLConnection connection = url.openConnection();
            connection.setRequestProperty(
                    "User-Agent",
                    Constants.programName
                            + " v"
                            + Constants.programVersion
                            + " java"
                            + System.getProperty("java.version"));
            InputStream is = connection.getInputStream();
            BufferedReader br =
                    new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String firstLine = br.readLine();
            if (firstLine != null) {
                ProgramVersion curVersion = ProgramVersion.getCurrentVersionNumber();
                ProgramVersion newestVersion = ProgramVersion.getSavedVersionNumber(firstLine);
                if (curVersion.compareTo(newestVersion) < 0) {
                    return true;
                }
            }
            return false;
        } catch (MalformedURLException | java.net.URISyntaxException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
