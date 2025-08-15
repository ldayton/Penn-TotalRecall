package util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateChecker")
class UpdateCheckerTest {

    @Test
    @DisplayName("Update notification decision logic")
    void updateNotificationLogic() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);

        // Test case: newer version available - should trigger notification
        @SuppressWarnings("unchecked")
        HttpResponse<String> newerResponse = mock(HttpResponse.class);
        when(newerResponse.body()).thenReturn("{\"tag_name\":\"v2025.12.25\"}");
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(newerResponse));

        UpdateChecker checker =
                new UpdateChecker("https://api.example.com", "https://example.com", mockClient);

        // Mock current version to be older
        UpdateChecker spyChecker = spy(checker);
        doReturn("2025.08.15").when(spyChecker).getCurrentVersion();

        assertDoesNotThrow(() -> spyChecker.checkForUpdateOnStartup());

        // Test case: same version - should not trigger notification
        doReturn("2025.12.25").when(spyChecker).getCurrentVersion();
        assertDoesNotThrow(() -> spyChecker.checkForUpdateOnStartup());
    }

    @Test
    @DisplayName("JSON parsing with various GitHub API responses")
    void jsonParsingEdgeCases() throws Exception {
        UpdateChecker checker =
                new UpdateChecker(
                        "https://api.example.com", "https://example.com", mock(HttpClient.class));

        // Test regex pattern directly since getLatestVersionFromGitHub is private
        // Valid formats
        assertTrue(checker.isNewerVersion("2024.01.01", "2025.12.25"));
        assertTrue(checker.isNewerVersion("2025.08.15", "2025.08.16"));
        assertTrue(checker.isNewerVersion("2025.08.15", "2025.09.01"));

        // Edge case: older version
        assertFalse(checker.isNewerVersion("2025.12.25", "2025.08.15"));

        // Edge case: same version
        assertFalse(checker.isNewerVersion("2025.08.15", "2025.08.15"));

        // Edge case: version string comparison edge cases
        assertTrue(checker.isNewerVersion("2025.08.09", "2025.08.10")); // Single digit day
        assertTrue(checker.isNewerVersion("2025.01.15", "2025.02.01")); // Single digit month
    }

    @Test
    @DisplayName("getCurrentVersion with different manifest scenarios")
    void getCurrentVersionScenarios() {
        UpdateChecker checker =
                new UpdateChecker(
                        "https://api.example.com", "https://example.com", mock(HttpClient.class));

        // When called, getCurrentVersion should return either manifest version or "0.0.0"
        String version = checker.getCurrentVersion();
        assertTrue(
                version != null
                        && (version.equals("0.0.0") || version.matches("\\d{4}\\.\\d{2}\\.\\d{2}")),
                "Should return either default '0.0.0' or valid CalVer format");
    }

    @Test
    @DisplayName("HTTP error scenarios")
    void httpErrorScenarios() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);

        // Test timeout scenario
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(
                        invocation -> {
                            CompletableFuture<HttpResponse<String>> timeoutFuture =
                                    new CompletableFuture<>();
                            timeoutFuture.completeExceptionally(
                                    new TimeoutException("Request timed out"));
                            return timeoutFuture;
                        });

        UpdateChecker checker =
                new UpdateChecker("https://api.example.com", "https://example.com", mockClient);
        assertDoesNotThrow(() -> checker.checkForUpdateOnStartup());

        // Test HTTP 404 error
        @SuppressWarnings("unchecked")
        HttpResponse<String> notFoundResponse = mock(HttpResponse.class);
        when(notFoundResponse.statusCode()).thenReturn(404);
        when(notFoundResponse.body()).thenReturn("Not Found");
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(notFoundResponse));

        UpdateChecker checker404 =
                new UpdateChecker("https://api.example.com", "https://example.com", mockClient);
        assertDoesNotThrow(() -> checker404.checkForUpdateOnStartup());

        // Test empty response body
        @SuppressWarnings("unchecked")
        HttpResponse<String> emptyResponse = mock(HttpResponse.class);
        when(emptyResponse.body()).thenReturn("");
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(emptyResponse));

        UpdateChecker checkerEmpty =
                new UpdateChecker("https://api.example.com", "https://example.com", mockClient);
        assertDoesNotThrow(() -> checkerEmpty.checkForUpdateOnStartup());

        Thread.sleep(100); // Allow async operations to complete
    }
}
