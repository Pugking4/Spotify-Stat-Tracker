package com.pugking4.spotifystat;

import com.pugking4.spotifystat.tracker.Cache;
import com.pugking4.spotifystat.tracker.OAuthServerConfig;
import com.pugking4.spotifystat.tracker.SpotifyApiException;
import com.pugking4.spotifystat.tracker.SpotifyOAuthServer;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SpotifyOAuthServerIT {
    @TempDir
    Path tempDir;

    @Test
    void test_startServer_createsKeystore_and_accepts_callback() throws Exception {
        Path keystorePath = tempDir.resolve("keystore.jks");
        OAuthServerConfig cfg = new OAuthServerConfig("127.0.0.1", 0, "testpass", keystorePath);

        InMemoryCache cache = new InMemoryCache();

        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(cfg.host(), cfg.port()), 0);
        SpotifyOAuthServer oauth = new SpotifyOAuthServer(cache, cfg, httpsServer);

        oauth.startServer();

        assertTrue(Files.exists(keystorePath), "Keystore should be created in temp dir");

        int port = httpsServer.getAddress().getPort();
        HttpClient client = insecureTestClient();

        HttpRequest req = HttpRequest.newBuilder(
                URI.create("https://127.0.0.1:" + port + "/callback?code=abc&state=xyz")
        ).GET().build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertEquals("abc", new String(cache.read("oauth_code.txt"), UTF_8));
    }

    private static HttpClient insecureTestClient() throws Exception {
        TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] xcs, String string) {}
            public void checkServerTrusted(X509Certificate[] xcs, String string) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());

        SSLParameters p = new SSLParameters();
        p.setEndpointIdentificationAlgorithm("");

        return HttpClient.newBuilder()
                .sslContext(sc)
                .sslParameters(p)
                .build();
    }

    static final class InMemoryCache implements Cache {
        private final java.util.Map<String, byte[]> m = new java.util.concurrent.ConcurrentHashMap<>();
        @Override public void write(String name, byte[] data) { m.put(name, data); }
        @Override public byte[] read(String name) { return m.getOrDefault(name, new byte[0]); }
    }

    @Test
    void test_server_bind_construction() throws Exception {
        Path keystorePath = tempDir.resolve("keystore.jks");
        OAuthServerConfig cfg = new OAuthServerConfig("127.0.0.1", 0, "testpass", keystorePath);

        InMemoryCache cache = new InMemoryCache();

        SpotifyOAuthServer oauth = new SpotifyOAuthServer(cache, cfg);

        oauth.startServer();

        assertThrows(IOException.class, () -> {
            new ServerSocket(oauth.getPort());
        });

        oauth.stopServer();
    }
}
