package com.pugking4.spotifystat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.tracker.SpotifyApiException;
import com.pugking4.spotifystat.tracker.SpotifyWrapper;
import com.pugking4.spotifystat.tracker.TokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.pugking4.spotifystat.TestUtilities.loadResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SpotifyWrapperTests {
    @Mock
    private HttpClient httpClient;
    @Mock
    private TokenManager tokenManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SpotifyWrapper spotifyWrapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        spotifyWrapper = new SpotifyWrapper(httpClient, objectMapper, tokenManager);
    }

    @Test
    void test_GetCurrentlyPlayingTrack_standard() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("currently-playing-miku.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = spotifyWrapper.getCurrentlyPlayingTrack();

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertAll("Currently playing response",
                () -> assertTrue((Boolean) result.get("is_playing")),
                () -> assertEquals(2978, result.get("progress_ms")),
                () -> assertEquals("Miku", ((Map) result.get("item")).get("name")),
                () -> assertEquals("7aux5UvnlBDYlrlwoczifW", ((Map) result.get("item")).get("id")),
                () -> {
                    List<?> artists = (List<?>) ((Map) result.get("item")).get("artists");
                    assertEquals(2, artists.size());
                    assertEquals("Anamanaguchi", ((Map) artists.get(0)).get("name"));
                }
        );
    }

    @Test
    void test_GetCurrentlyPlayingTrack_empty() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = "{}";

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(204);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = spotifyWrapper.getCurrentlyPlayingTrack();

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertTrue(result.isEmpty());
    }

    @Test
    void test_GetCurrentlyPlayingTrack_blank() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = "";

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(204);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = spotifyWrapper.getCurrentlyPlayingTrack();

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertTrue(result.isEmpty());
    }

    @Test
    void test_GetCurrentlyPlayingTrack_paused() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("currently-playing-crazy-paused.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        Map<String, Object> result = spotifyWrapper.getCurrentlyPlayingTrack();

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertFalse((Boolean) result.get("is_playing"));
    }

    @Test
    void test_GetCurrentlyPlayingTrack_unauthorised() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("currently-playing-unauthorised.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        SpotifyApiException ex = assertThrows(SpotifyApiException.class, () -> {
            spotifyWrapper.getCurrentlyPlayingTrack();
        });

        // Verify
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    void test_GetCurrentlyPlayingTrack_bad_oauth() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("currently-playing-bad-oauth.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(403);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        SpotifyApiException ex = assertThrows(SpotifyApiException.class, () -> {
            spotifyWrapper.getCurrentlyPlayingTrack();
        });

        // Verify
        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void test_GetCurrentlyPlayingTrack_rate_limited() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("currently-playing-rate-limited.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(429);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        SpotifyApiException ex = assertThrows(SpotifyApiException.class, () -> {
            spotifyWrapper.getCurrentlyPlayingTrack();
        });

        // Verify
        assertEquals(429, ex.getStatusCode());
    }

    @Test
    void test_GetCurrentlyPlayingTrack_unexpected_error() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("currently-playing-unexpected-error.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(451);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        SpotifyApiException ex = assertThrows(SpotifyApiException.class, () -> {
            spotifyWrapper.getCurrentlyPlayingTrack();
        });

        // Verify
        assertEquals(451, ex.getStatusCode());
    }

    @Test
    void test_getAvailableDevices_standard() throws IOException, InterruptedException {
        // Set-up
        String jsonResponse = loadResource("available-devices.json");

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        List<Map<String, Object>> result = spotifyWrapper.getAvailableDevices();

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertAll("Available devices response",
                () -> assertEquals(2, result.size()),
                () -> assertEquals("a40ca27e3e668bfa16c91b7395bfdb98b73c6bf0", result.get(0).get("id")),
                () -> assertFalse((Boolean) result.get(0).get("is_active")),
                () -> assertEquals("ea36125e0bdd79f816f1fec37a7eb049e07e668c", result.get(1).get("id")),
                () -> assertTrue((Boolean) result.get(1).get("is_active"))
        );
    }

    @Test
    void test_getBatchArtists_standard() throws IOException, InterruptedException {
        String jsonResponse = loadResource("batch-artists-5.json");
        List<String> artistIds = List.of("6gsYua8nnnutLOGReIJHsK",
                "2hYjPkmTry3LYVVSymws5i",
                "7gFHp9H8K8h4B9y7HkFC7N",
                "4f2l5pSKd1oUMEMx7SZBng",
                "1Y81Ch90opScfpMfN17lZb"
        );

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        List<Map<String, Object>> result = spotifyWrapper.getBatchArtists(artistIds);

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertAll("batch artists response",
                () -> assertEquals(5, result.size()),
                () -> assertEquals(171027, (Integer) ((Map<String, Object>) result.get(1).get("followers")).get("total")),
                () -> assertTrue(((List<String>) result.get(2).get("genres")).isEmpty()),
                () -> {
                    for (String genre : (List<String>) result.get(4).get("genres")) {
                        assertTrue(List.of("speedcore", "breakcore", "hardcore techno", "gabber", "hardcore", "happy hardcore").contains(genre));
                    }
                },
                () -> {
                    for (int i = 0; i < result.size(); i++) {
                        assertEquals(artistIds.get(i).strip(), ((String) result.get(i).get("id")).strip());
                    }
                },
                () -> {
                    Instant now = Instant.now();
                    for (Map<String, Object> artist : result) {
                        Instant updated = (Instant) artist.get("updated_at");
                        assertTrue(updated.isAfter(now.minusSeconds(5)));  // After 5s ago
                        assertTrue(Math.abs(Duration.between(updated, now).getSeconds()) < 5);
                    }
                }
        );
    }

    @Test
    void test_getBatchArtists_max_load() throws IOException, InterruptedException {
        String jsonResponse = loadResource("batch-artists-50.json");
        List<String> artistIds = List.of(loadResource("batch-artists-50.csv").split("\n"));

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        List<Map<String, Object>> result = spotifyWrapper.getBatchArtists(artistIds);

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertAll("batch artists response",
                () -> assertEquals(50, result.size()),
                () -> {
                    for (int i = 0; i < result.size(); i++) {
                        assertEquals(artistIds.get(i).strip(), ((String) result.get(i).get("id")).strip());
                    }
                },
                () -> {
                    Instant now = Instant.now();
                    for (Map<String, Object> artist : result) {
                        Instant updated = (Instant) artist.get("updated_at");
                        assertTrue(updated.isAfter(now.minusSeconds(5)));  // After 5s ago
                        assertTrue(Math.abs(Duration.between(updated, now).getSeconds()) < 5);
                    }
                }
        );
    }

    /*@Test
    void test_getBatchArtists_empty() throws IOException, InterruptedException {
        String jsonResponse = "{}";
        List<String> artistIds = List.of();

        when(tokenManager.getAccessToken()).thenReturn("fake-token");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        // Act
        List<Map<String, Object>> result = spotifyWrapper.getBatchArtists(artistIds);

        // Verify
        verify(httpClient).send(argThat(req -> req.headers().firstValue("Authorization").orElse("").contains("fake-token")), any());

        assertAll("batch artists response",
                () -> assertEquals(0, result.size()),
                () -> assertEquals(171027, (Integer) ((Map<String, Object>) result.get(1).get("followers")).get("total")),
                () -> assertTrue(((List<String>) result.get(2).get("genres")).isEmpty()),
                () -> {
                    for (String genre : (List<String>) result.get(4).get("genres")) {
                        assertTrue(List.of("speedcore", "breakcore", "hardcore techno", "gabber", "hardcore", "happy hardcore").contains(genre));
                    }
                },
                () -> {
                    for (int i = 0; i < result.size(); i++) {
                        assertEquals(artistIds.get(i).strip(), ((String) result.get(i).get("id")).strip());
                    }
                },
                () -> {
                    Instant now = Instant.now();
                    for (Map<String, Object> artist : result) {
                        Instant updated = (Instant) artist.get("updated_at");
                        assertTrue(updated.isAfter(now.minusSeconds(5)));  // After 5s ago
                        assertTrue(Math.abs(Duration.between(updated, now).getSeconds()) < 5);
                    }
                }
        );
    }*/
}
