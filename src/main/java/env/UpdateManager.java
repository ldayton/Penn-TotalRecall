package env;

import di.GuiceBootstrap;
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
import javax.swing.SwingUtilities;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.DialogService;

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
    private final HttpClient httpClient;

    @Inject
    public UpdateManager(@NonNull AppConfig appConfig, @NonNull HttpClient httpClient) {
        this.appConfig = appConfig;
        this.httpClient = httpClient;
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
                                SwingUtilities.invokeLater(
                                        () -> showUpdateNotification(newVersion, releasesPageUrl));
                            }
                        });
    }

    String getCurrentVersion() {
        Package pkg = getClass().getPackage();
        String version = pkg.getImplementationVersion();
        return version != null ? version : "0.0.0";
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

        var tagPattern =
                Pattern.compile(
                        """
                        "tag_name"\\s*:\\s*"v([^"]+)"
                        """
                                .strip());

        var matcher = tagPattern.matcher(response.body());
        return matcher.find() ? matcher.group(1) : getCurrentVersion();
    }

    private void showUpdateNotification(
            @NonNull String newVersion, @NonNull String releasesPageUrl) {
        var message =
                """
                A new version (%s) is available.

                Download from: %s
                """
                        .formatted(newVersion, releasesPageUrl);

        DialogService dialogService = GuiceBootstrap.getInjectedInstance(DialogService.class);
        if (dialogService != null) {
            dialogService.showInfo(message);
        }
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
