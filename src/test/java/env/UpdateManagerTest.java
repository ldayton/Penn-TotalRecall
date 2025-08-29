package env;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ui.DialogService;

/** Tests for UpdateManager functionality. */
class UpdateManagerTest {

    @Test
    @DisplayName("Update checking with valid configuration")
    void updateCheckingWithValidConfig() {
        AppConfig mockConfig = mock(AppConfig.class);
        ProgramVersion mockProgramVersion = mock(ProgramVersion.class);
        HttpClient mockClient = mock(HttpClient.class);
        DialogService mockDialogService = mock(DialogService.class);

        when(mockConfig.getProperty("releases.api.url")).thenReturn("https://api.example.com");
        when(mockConfig.getProperty("releases.page.url")).thenReturn("https://example.com");
        when(mockProgramVersion.toString()).thenReturn("2025.08.15");

        // Test case: newer version available - should trigger notification
        @SuppressWarnings("unchecked")
        HttpResponse<String> newerResponse = mock(HttpResponse.class);
        when(newerResponse.body()).thenReturn("{\"tag_name\":\"v2025.12.25\"}");
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(_ -> CompletableFuture.completedFuture(newerResponse));

        UpdateManager manager =
                new UpdateManager(mockConfig, mockProgramVersion, mockClient, mockDialogService);

        // Mock current version to be older
        UpdateManager spyManager = spy(manager);
        doReturn("2025.08.15").when(spyManager).getCurrentVersion();

        assertDoesNotThrow(() -> spyManager.checkForUpdateOnStartup());

        // Test case: same version - should not trigger notification
        doReturn("2025.12.25").when(spyManager).getCurrentVersion();
        assertDoesNotThrow(() -> spyManager.checkForUpdateOnStartup());
    }

    @Test
    @DisplayName("JSON parsing with various GitHub API responses")
    void jsonParsingEdgeCases() throws Exception {
        AppConfig mockConfig = mock(AppConfig.class);
        DialogService mockDialogService = mock(DialogService.class);
        UpdateManager manager =
                new UpdateManager(
                        mockConfig,
                        mock(ProgramVersion.class),
                        mock(HttpClient.class),
                        mockDialogService);

        // Test version comparison logic directly
        // Valid formats
        assertTrue(manager.isNewerVersion("2024.01.01", "2025.12.25"));
        assertTrue(manager.isNewerVersion("2025.08.15", "2025.08.16"));
        assertTrue(manager.isNewerVersion("2025.08.15", "2025.09.01"));

        // Edge case: older version
        assertFalse(manager.isNewerVersion("2025.12.25", "2025.08.15"));

        // Edge case: same version
        assertFalse(manager.isNewerVersion("2025.08.15", "2025.08.15"));

        // Edge case: version string comparison edge cases
        assertTrue(manager.isNewerVersion("2025.08.09", "2025.08.10")); // Single digit day
        assertTrue(manager.isNewerVersion("2025.01.15", "2025.02.01")); // Single digit month
    }

    @Test
    @DisplayName("getCurrentVersion with different manifest scenarios")
    void getCurrentVersionScenarios() {
        AppConfig mockConfig = mock(AppConfig.class);
        ProgramVersion mockProgramVersion = mock(ProgramVersion.class);
        DialogService mockDialogService = mock(DialogService.class);

        // Configure mock to return the actual version from application.properties
        when(mockProgramVersion.toString()).thenReturn("2025.08.15");

        UpdateManager manager =
                new UpdateManager(
                        mockConfig, mockProgramVersion, mock(HttpClient.class), mockDialogService);

        // When called, getCurrentVersion should return the version from ProgramVersion
        String version = manager.getCurrentVersion();
        assertEquals("2025.08.15", version, "Should return version from ProgramVersion");
    }

    @Test
    @DisplayName("HTTP error scenarios")
    void httpErrorScenarios() throws Exception {
        AppConfig mockConfig = mock(AppConfig.class);
        HttpClient mockClient = mock(HttpClient.class);
        DialogService mockDialogService = mock(DialogService.class);

        when(mockConfig.getProperty("releases.api.url")).thenReturn("https://api.example.com");
        when(mockConfig.getProperty("releases.page.url")).thenReturn("https://example.com");

        // Test timeout scenario
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(
                        _ -> {
                            CompletableFuture<HttpResponse<String>> timeoutFuture =
                                    new CompletableFuture<>();
                            timeoutFuture.completeExceptionally(
                                    new TimeoutException("Request timed out"));
                            return timeoutFuture;
                        });

        UpdateManager manager =
                new UpdateManager(
                        mockConfig, mock(ProgramVersion.class), mockClient, mockDialogService);
        assertDoesNotThrow(() -> manager.checkForUpdateOnStartup());

        // Test HTTP 404 error
        @SuppressWarnings("unchecked")
        HttpResponse<String> notFoundResponse = mock(HttpResponse.class);
        when(notFoundResponse.statusCode()).thenReturn(404);
        when(notFoundResponse.body()).thenReturn("Not Found");
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(_ -> CompletableFuture.completedFuture(notFoundResponse));

        UpdateManager manager404 =
                new UpdateManager(
                        mockConfig, mock(ProgramVersion.class), mockClient, mockDialogService);
        assertDoesNotThrow(() -> manager404.checkForUpdateOnStartup());

        // Test empty response body
        @SuppressWarnings("unchecked")
        HttpResponse<String> emptyResponse = mock(HttpResponse.class);
        when(emptyResponse.body()).thenReturn("");
        when(mockClient.sendAsync(any(), any()))
                .thenAnswer(_ -> CompletableFuture.completedFuture(emptyResponse));

        UpdateManager managerEmpty =
                new UpdateManager(
                        mockConfig, mock(ProgramVersion.class), mockClient, mockDialogService);
        assertDoesNotThrow(() -> managerEmpty.checkForUpdateOnStartup());

        Thread.sleep(100); // Allow async operations to complete
    }

    @Test
    @DisplayName("Configuration integration")
    void configurationIntegration() {
        AppConfig mockConfig = mock(AppConfig.class);
        HttpClient mockClient = mock(HttpClient.class);
        DialogService mockDialogService = mock(DialogService.class);

        when(mockConfig.getProperty("releases.api.url")).thenReturn("https://api.test.com");
        when(mockConfig.getProperty("releases.page.url")).thenReturn("https://test.com");

        UpdateManager manager =
                new UpdateManager(
                        mockConfig, mock(ProgramVersion.class), mockClient, mockDialogService);

        assertEquals("https://api.test.com", manager.getReleasesApiUrl());
        assertEquals("https://test.com", manager.getReleasesPageUrl());

        // Test missing URLs disable update checking
        when(mockConfig.getProperty("releases.api.url")).thenReturn(null);
        when(mockConfig.getProperty("releases.page.url")).thenReturn("https://test.com");

        assertDoesNotThrow(() -> manager.checkForUpdateOnStartup());
        verifyNoInteractions(mockClient); // Should not make HTTP calls
    }
}
