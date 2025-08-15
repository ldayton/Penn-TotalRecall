package util;

import env.AppConfig;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple update checker using GitHub Releases API. */
public class UpdateChecker {
    private static final Logger logger = LoggerFactory.getLogger(UpdateChecker.class);

    private final String releasesApiUrl;
    private final String releasesPageUrl;
    private final HttpClient httpClient;

    public UpdateChecker(@NonNull AppConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    @Inject
    public UpdateChecker(@NonNull AppConfig config, @NonNull HttpClient httpClient) {
        this.releasesApiUrl = config.getReleasesApiUrl();
        this.releasesPageUrl = config.getReleasesPageUrl();
        this.httpClient = httpClient;
    }

    public UpdateChecker(
            @NonNull String releasesApiUrl,
            @NonNull String releasesPageUrl,
            @NonNull HttpClient httpClient) {
        this.releasesApiUrl = releasesApiUrl;
        this.releasesPageUrl = releasesPageUrl;
        this.httpClient = httpClient;
    }

    /** Check for updates on startup (async, non-blocking). */
    public void checkForUpdateOnStartup() {
        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                String current = getCurrentVersion();
                                String latest = getLatestVersionFromGitHub();
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
                                        () -> showUpdateNotification(newVersion));
                            }
                        });
    }

    String getCurrentVersion() {
        Package pkg = getClass().getPackage();
        String version = pkg.getImplementationVersion();
        return version != null ? version : "0.0.0";
    }

    private String getLatestVersionFromGitHub() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(releasesApiUrl))
                        .timeout(Duration.ofSeconds(5))
                        .build();

        CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        HttpResponse<String> response = future.get(5, TimeUnit.SECONDS);

        // Simple JSON parsing - find "tag_name":"v2024.12.1"
        String tagPattern = "\"tag_name\":\"v([^\"]+)\"";
        Pattern pattern = Pattern.compile(tagPattern);
        Matcher matcher = pattern.matcher(response.body());

        return matcher.find() ? matcher.group(1) : getCurrentVersion();
    }

    private void showUpdateNotification(String newVersion) {
        String message =
                String.format(
                        """
                        A new version (%s) is available.

                        Download from: %s""",
                        newVersion, releasesPageUrl);

        GiveMessage.infoMessage(message);
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
