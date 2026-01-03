package com.pugking4.spotifystat.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.common.logging.Logger;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

// https://refactoring.guru/design-patterns/singleton/java/example#example-2
public class TokenManager {
    private static volatile TokenManager instance;
    private static final Object LOCK = new Object();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache cache;
    private final SpotifyOAuthServer oAuthServer;
    private final Sleeper sleeper;
    private final long pollDelayMs;

    private final TokenManagerConfig cfg;

    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.now();
    private volatile String refreshToken;

    private static final String REFRESH_TOKEN_FILENAME = "refresh_token.txt";
    private static final String OAUTH_CODE_FILENAME = "oauth_code.txt";

    private TokenManager(HttpClient httpClient, ObjectMapper objectMapper, Cache cache, TokenManagerConfig cfg, SpotifyOAuthServer oAuthServer, Sleeper sleeper, long pollDelayMs) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.cache = cache;
        this.cfg = cfg;
        this.refreshToken = readRefreshToken();
        this.oAuthServer = oAuthServer;
        this.sleeper = sleeper;
        this.pollDelayMs = pollDelayMs;

        if (this.refreshToken.isEmpty()) {
            NewTokens newTokens = startAuthorisationWorkflow();
            setAccessToken(newTokens.accessToken(), newTokens.expiry());
            setRefreshToken(newTokens.refreshToken());
        }
    }

    public static TokenManager create(HttpClient client, ObjectMapper mapper, Cache cache, TokenManagerConfig cfg, SpotifyOAuthServer oAuthServer, Sleeper sleeper, long pollDelayMs) {
        return new TokenManager(client, mapper, cache, cfg, oAuthServer, sleeper, pollDelayMs);
    }

    private void setAccessToken(String accessToken, Instant expiry) {
        this.accessToken = accessToken;
        this.accessTokenExpiry = expiry;
    }

    private void setRefreshToken(String refreshToken) {
        try {
            cache.write(REFRESH_TOKEN_FILENAME, refreshToken.getBytes(StandardCharsets.UTF_8));
            this.refreshToken = refreshToken;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String getAuthorisationURI() {
        try {
            URIBuilder authURIBuilder = new URIBuilder("https://accounts.spotify.com/authorize")
                    .setParameter("client_id", cfg.clientId())
                    .setParameter("response_type", "code")
                    .setParameter("redirect_uri", cfg.redirectUri())
                    .setParameter("scope", "user-read-currently-playing user-read-playback-state");

            URI authURI = authURIBuilder.build();

            return authURI.toString();

        } catch (URISyntaxException | IllegalStateException e) {
            throw new SpotifyAuthenticationException(-1, e.getMessage());
        }
    }

    private NewTokens startAuthorisationWorkflow() {
        oAuthServer.startServer();
        Logger.println(getAuthorisationURI(), 1);
        String code = readOAuthCode();
        while (code.isEmpty()) {
            try {
                sleeper.sleep(pollDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            code = readOAuthCode();
        }

        return requestTokens(code);
    }

    private NewTokens requestTokens(String code) {
        Logger.println("Requesting access token.");
        try {
            URI tokenURI = URI.create("https://accounts.spotify.com/api/token");

            String body =
                    "grant_type=authorization_code" +
                            "&code=" + code +
                            "&redirect_uri=" + cfg.redirectUri();

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + cfg.authorisationHeaderValue())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();


            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), Map.class);

            if (tokenResponse.containsKey("error")) {
                Map<String, Object> errorMap = (Map<String, Object>) tokenResponse.get("error");
                Logger.println("Problem with getting tokens: " + errorMap.get("message"));
                throw new SpotifyAuthenticationException((Integer) errorMap.get("status"), (String) errorMap.get("message"));
            }


            String accessToken = (String) tokenResponse.get("access_token");
            Instant accessTokenExpiry = Instant.now().plusMillis((Integer) tokenResponse.get("expires_in"));
            String refreshToken = (String) tokenResponse.get("refresh_token");
            Logger.println("Gotten new refresh token: " + refreshToken);
            return new NewTokens(accessToken, refreshToken, accessTokenExpiry);
        } catch (IOException | InterruptedException e) {
            throw new SpotifyAuthenticationException(-1, e.getMessage());
        }
    }

    private NewTokens refreshAccessToken() {
        try {
            URI tokenURI = URI.create("https://accounts.spotify.com/api/token");

            String body =
                    "grant_type=refresh_token" +
                            "&refresh_token=" + refreshToken;

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + cfg.authorisationHeaderValue())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();


            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), Map.class);

            if (tokenResponse.containsKey("error")) {
                Logger.println("Refresh token invalid, need full authorization: " + tokenResponse.get("error_description"));
                return startAuthorisationWorkflow();
            }

            if (tokenResponse.containsKey("refresh_token")) {
                refreshToken = (String) tokenResponse.get("refresh_token");
            }
            int expirySeconds = (Integer) tokenResponse.get("expires_in");
            String accessToken = (String) tokenResponse.get("access_token");
            return new NewTokens(accessToken, refreshToken, Instant.now().plusSeconds(expirySeconds));

        } catch (IOException | InterruptedException e) {
            throw new SpotifyAuthenticationException(-1, e.getMessage());
        }
    };

    private String readOAuthCode() {
        try {
            return new String(cache.read(OAUTH_CODE_FILENAME), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SpotifyAuthenticationException(-1, e.getMessage());
        }
    }

    private String readRefreshToken() {
        try {
            byte[] bytes = cache.read(REFRESH_TOKEN_FILENAME);
            if (bytes == null || bytes.length == 0) return "";
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SpotifyAuthenticationException(-1, e.getMessage());
        }
    }

    public static TokenManager getInstance() {
        TokenManager result = instance;
        if (result != null) {
            return result;
        }
        synchronized (TokenManager.class) {
            if (instance == null) {
                instance = createInstance();
            }
            return instance;
        }
    }

    @ExcludeFromJacocoGeneratedReport
    private static TokenManager createInstance() {
        Dotenv dotenv = Dotenv.load();
        return new TokenManager(
                HttpClient.newBuilder().build(),
                new ObjectMapper(),
                new FileCache(),
                new TokenManagerConfig(dotenv.get("CLIENT_ID"), dotenv.get("CLIENT_SECRET"), dotenv.get("REDIRECT_URI")),
                new SpotifyOAuthServer(
                        new FileCache(),
                        new OAuthServerConfig(dotenv.get("HOST"), Integer.parseInt(dotenv.get("PORT")), dotenv.get("KEYSTORE_PASSWORD"))
                ),
                Thread::sleep,
                10000
        );
    }

    public String getAccessToken() {
        synchronized (LOCK) {
            if (Instant.now().isAfter(accessTokenExpiry) || accessToken == null) {
                NewTokens newTokens = refreshAccessToken();
                setAccessToken(newTokens.accessToken(), newTokens.expiry());
                setRefreshToken(newTokens.refreshToken());
            }
            return accessToken;
        }
    }
}


