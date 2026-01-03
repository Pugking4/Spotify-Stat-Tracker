package com.pugking4.spotifystat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.tracker.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static com.pugking4.spotifystat.TestUtilities.loadResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TokenManagerTests {
    @Mock
    private HttpClient httpClient;
    @Mock
    private Cache cache;
    @Mock
    private SpotifyOAuthServer oAuthServer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TokenManager tokenManager;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        MockitoAnnotations.openMocks(this);
    }

    TokenManagerConfig defaultConfig() {
        return new TokenManagerConfig("client-id", "client-secret", "redirect-uri");
    }

    void createDefaultTokenManager() throws IOException {
        when(cache.read("oauth_code.txt")).thenReturn("valid-code".getBytes(StandardCharsets.UTF_8));
        when(cache.read("refresh_token.txt")).thenReturn("valid-refresh".getBytes(StandardCharsets.UTF_8));

        createTokenManager(defaultConfig());
    }

    void createTokenManager(TokenManagerConfig cfg) throws IOException {
        tokenManager = TokenManager.create(httpClient, objectMapper, cache, cfg, oAuthServer, ms -> {}, 0);
    }

    @Test
    void test_getAccessToken_not_expired_returns_new() throws IOException, InterruptedException {
        createDefaultTokenManager();

        String jsonResponse = loadResource("refresh-token.json");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        String result = tokenManager.getAccessToken();

        assertEquals("access-token", result);
    }

    @Test
    void test_getAccessToken_expired_returns_new() throws IOException, InterruptedException {
        createDefaultTokenManager();

        String jsonResponseExpired = loadResource("refresh-token-expired.json");
        String jsonResponse = loadResource("refresh-token.json");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200, 200);
        when(mockResponse.body()).thenReturn(jsonResponseExpired, jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        String expiredResult = tokenManager.getAccessToken();
        String result = tokenManager.getAccessToken();

        assertEquals("access-token", result);
        assertEquals("access-token-expired", expiredResult);
        assertEquals("access-token", result);
    }

    @Test
    void test_getAccessToken_missing_refresh_token() throws IOException, InterruptedException {
        when(cache.read("refresh_token.txt")).thenReturn(new byte[0]);
        when(cache.read("oauth_code.txt")).thenReturn(new byte[0]);
        doAnswer(invocation -> {
            when(cache.read("oauth_code.txt"))
                    .thenReturn("new-code".getBytes(StandardCharsets.UTF_8));
            return null; // void method
        }).when(oAuthServer).startServer();

        String jsonResponse = loadResource("request-tokens.json");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        createTokenManager(defaultConfig());

        String result = tokenManager.getAccessToken();

        assertEquals("new-access-token", result);
    }

    @Test
    void test_getAccessToken_missing_refresh_token_delayed() throws IOException, InterruptedException {
        when(cache.read("refresh_token.txt")).thenReturn(new byte[0]);
        when(cache.read("oauth_code.txt")).thenReturn(new byte[0]);
        doAnswer(invocation -> {
            when(cache.read("oauth_code.txt"))
                    .thenReturn(new byte[0], new byte[0], new byte[0], "new-code".getBytes(StandardCharsets.UTF_8));
            return null; // void method
        }).when(oAuthServer).startServer();

        String jsonResponse = loadResource("request-tokens.json");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        createTokenManager(defaultConfig());

        String result = tokenManager.getAccessToken();

        assertEquals("new-access-token", result);
    }

    @Test
    void test_getAccessToken_invalid_refresh_token() throws IOException, InterruptedException {
        when(cache.read("refresh_token.txt")).thenReturn("invalid-refresh-token".getBytes(StandardCharsets.UTF_8));
        when(cache.read("oauth_code.txt")).thenReturn(new byte[0]);
        doAnswer(invocation -> {
            when(cache.read("oauth_code.txt"))
                    .thenReturn("new-code".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(oAuthServer).startServer();

        String invalidRefreshResponse = loadResource("refresh-token-error.json");
        String validTokensResponse = loadResource("request-tokens.json");
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400, 200);
        when(mockResponse.body()).thenReturn(invalidRefreshResponse, validTokensResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        createTokenManager(defaultConfig());

        String result = tokenManager.getAccessToken();

        assertEquals("new-access-token", result);
    }

    @Test
    void getInstance_returns_preseeded_instance() throws Exception {
        TokenManager fake = mock(TokenManager.class);
        Field f = TokenManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, fake);

        TokenManager actual = TokenManager.getInstance();

        assertSame(fake, actual);
    }

    @Test
    void test_redirect_uri_mismatch() throws IOException, InterruptedException {
        when(cache.read("refresh_token.txt")).thenReturn("invalid-refresh-token".getBytes(StandardCharsets.UTF_8));
        when(cache.read("oauth_code.txt")).thenReturn(new byte[0]);
        doAnswer(invocation -> {
            when(cache.read("oauth_code.txt"))
                    .thenReturn("new-code".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(oAuthServer).startServer();

        HttpResponse<String> refreshError = mock(HttpResponse.class);
        when(refreshError.body()).thenReturn(loadResource("refresh-token-error.json"));

        HttpResponse<String> redirectMismatch = mock(HttpResponse.class);
// IMPORTANT: match the JSON shape your code expects in requestTokens():
// {"error": {"status": 400, "message": "wrong-redirect-uri"}}
        when(redirectMismatch.body()).thenReturn(loadResource("request-tokens-redirect-uri-mismatch.json"));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(refreshError, redirectMismatch);  // consecutive calls [web:1021]

        createTokenManager(new TokenManagerConfig("client-id", "client-secret", "wrong-redirect-uri"));

        SpotifyAuthenticationException ex =
                assertThrows(SpotifyAuthenticationException.class, () -> tokenManager.getAccessToken());

// Optional: assert details
        assertEquals(400, ex.getStatusCode());
        assertEquals("Mismatched redirect uri", ex.getMessage());

    }


}

