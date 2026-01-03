package com.pugking4.spotifystat;

import com.pugking4.spotifystat.tracker.Cache;
import com.pugking4.spotifystat.tracker.OAuthServerConfig;
import com.pugking4.spotifystat.tracker.SpotifyOAuthServer;
import com.pugking4.spotifystat.tracker.SpotifyWrapper;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class SpotifyOAuthServerTests {
    @Mock
    private Cache cache;
    @Mock
    private OAuthServerConfig cfg;
    @Mock
    private HttpsServer server;

    private SpotifyOAuthServer oAuthServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        oAuthServer = new SpotifyOAuthServer(cache, cfg, server);
    }

    @Test
    void test_callbackHandle_writes_code_and_stops_server() throws Exception {
        SpotifyOAuthServer s = new SpotifyOAuthServer(cache, cfg, server);

        HttpExchange ex = mock(HttpExchange.class);
        HttpContext ctx = mock(HttpContext.class);

        when(ex.getRequestURI()).thenReturn(new URI("/callback?code=abc&state=xyz"));
        when(ex.getHttpContext()).thenReturn(ctx);
        when(ctx.getServer()).thenReturn(server);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(ex.getResponseBody()).thenReturn(baos);

        s.callbackHandler().handle(ex);

        verify(cache).write(eq("oauth_code.txt"), eq("abc".getBytes(StandardCharsets.UTF_8)));
        verify(ex).sendResponseHeaders(eq(200), anyLong());
        verify(server).stop(0);
        assertTrue(baos.toString(UTF_8).contains("Authorization code received: abc"));
    }




}
