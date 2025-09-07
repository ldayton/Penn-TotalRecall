package core.env;

import events.EventDispatchBus;
import events.InfoRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application update checking using GitHub Releases API.
 *
 * <p>Consolidates all update-related functionality including URL configuration and asynchronous
 * update checking with user notifications.
 */
@Singleton
public class UpdateManager {
    private static final Logger logger = LoggerFactory.getLogger(UpdateManager.class);

    // Configuration keys for update checking
    private static final String RELEASES_API_URL_KEY = "releases.api.url";
    private static final String RELEASES_PAGE_URL_KEY = "releases.page.url";

    private final AppConfig appConfig;
    private final ProgramVersion programVersion;
    private final HttpClient httpClient;
    private final EventDispatchBus eventBus;

    @Inject
    public UpdateManager(
            @NonNull AppConfig appConfig,
            @NonNull ProgramVersion programVersion,
            @NonNull HttpClient httpClient,
            @NonNull EventDispatchBus eventBus) {
        this.appConfig = appConfig;
        this.programVersion = programVersion;
        this.httpClient = httpClient;
        this.eventBus = eventBus;
    }

    /**
     * Gets the GitHub Releases API URL for update checking.
     *
     * @return the releases API URL
     */
    public String getReleasesApiUrl() {
        return appConfig.getProperty(RELEASES_API_URL_KEY);
    }

    /**
     * Gets the GitHub Releases page URL for user downloads.
     *
     * @return the releases page URL
     */
    public String getReleasesPageUrl() {
        return appConfig.getProperty(RELEASES_PAGE_URL_KEY);
    }

    /** Check for updates on startup (async, non-blocking). */
    public void checkForUpdateOnStartup() {
        String releasesApiUrl = getReleasesApiUrl();
        String releasesPageUrl = getReleasesPageUrl();

        if (releasesApiUrl == null || releasesPageUrl == null) {
            logger.debug("Update checking disabled - URLs not configured");
            return;
        }

        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                String current = getCurrentVersion();
                                String latest = getLatestVersionFromGitHub(releasesApiUrl);
                                return isNewerVersion(current, latest) ? latest : null;
                            } catch (Exception e) {
                                logger.warn("Update check failed: {}", e.getMessage());
                                return null; // Fail silently
                            }
                        })
                .thenAccept(
                        newVersion -> {
                            if (newVersion != null) {
                                showUpdateNotification(newVersion, releasesPageUrl);
                            }
                        });
    }

    /**
     * Manual update check invoked by the user. Always provides feedback.
     *
     * <p>Shows an update notification if a newer version is available, otherwise shows a simple
     * "You're up to date" info dialog. Runs asynchronously and returns immediately.
     */
    public void checkForUpdateManually() {
        String releasesApiUrl = getReleasesApiUrl();
        String releasesPageUrl = getReleasesPageUrl();

        if (releasesApiUrl == null || releasesPageUrl == null) {
            logger.debug("Manual update check skipped - URLs not configured");
            return;
        }

        CompletableFuture.runAsync(
                () -> {
                    try {
                        String current = getCurrentVersion();
                        String latest = getLatestVersionFromGitHub(releasesApiUrl);
                        if (isNewerVersion(current, latest)) {
                            showUpdateNotification(latest, releasesPageUrl);
                        } else {
                            eventBus.publish(
                                    new InfoRequestedEvent("You're up to date (" + current + ")"));
                        }
                    } catch (Exception e) {
                        logger.warn("Manual update check failed: {}", e.getMessage());
                        eventBus.publish(new InfoRequestedEvent("Could not check for updates."));
                    }
                });
    }

    String getCurrentVersion() {
        return programVersion.toString();
    }

    private String getLatestVersionFromGitHub(@NonNull String releasesApiUrl) throws Exception {
        var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(releasesApiUrl))
                        .timeout(Duration.ofSeconds(5))
                        .build();

        var response =
                httpClient
                        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .get(5, TimeUnit.SECONDS);

        // Require successful response
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP status " + response.statusCode());
        }

        var tagPattern =
                Pattern.compile(
                        """
                        "tag_name"\\s*:\\s*"v([^"]+)"
                        """
                                .strip());

        var matcher = tagPattern.matcher(response.body());
        if (matcher.find()) {
            return matcher.group(1);
        }

        // No releases found
        throw new IllegalStateException("No releases found (missing tag_name)");
    }

    private void showUpdateNotification(
            @NonNull String newVersion, @NonNull String releasesPageUrl) {
        var message =
                """
                A new version (%s) is available.

                Download from: %s
                """
                        .formatted(newVersion, releasesPageUrl);

        eventBus.publish(new InfoRequestedEvent(message));
    }

    boolean isNewerVersion(@NonNull String current, @NonNull String latest) {
        return compareCalVer(current, latest) < 0;
    }

    /**
     * Compare two CalVer versions. Format: YYYY.MM.DD Returns: negative if v1 < v2, zero if equal,
     * positive if v1 > v2
     */
    private int compareCalVer(@NonNull String v1, @NonNull String v2) {
        return v1.compareTo(v2);
    }
}
