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
import java.util.Base64;
import java.util.Map;

// https://refactoring.guru/design-patterns/singleton/java/example#example-2
public class TokenManager {
    private static volatile TokenManager instance;
    private static final Object LOCK = new Object();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String CLIENT_SECRET;
    private final String CLIENT_ID;
    private final String REDIRECT_URI;
    private final String REFRESH_TOKEN_FILENAME = "refresh_token.txt";
    private final String OAUTH_FILENAME = "oauth_code.txt";
    private final String AUTHORISATION_STRING;

    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.now();
    private volatile String refreshToken;

    private TokenManager(HttpClient httpClient, ObjectMapper objectMapper) throws InterruptedException, IOException {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        Dotenv dotenv = Dotenv.configure().directory(".").load();
        this.CLIENT_SECRET = dotenv.get("CLIENT_SECRET");
        this.CLIENT_ID = dotenv.get("CLIENT_ID");
        this.REDIRECT_URI = dotenv.get("REDIRECT_URI");
        this.AUTHORISATION_STRING = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        this.refreshToken = readRefreshToken();

        if (this.refreshToken.isEmpty()) {
            try {
                NewTokens newTokens = startAuthorisationWorkflow();
                setAccessToken(newTokens.accessToken(), newTokens.expiry());
                setRefreshToken(newTokens.refreshToken());

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setAccessToken(String accessToken, Instant expiry) {
        this.accessToken = accessToken;
        this.accessTokenExpiry = expiry;
    }

    private void setRefreshToken(String refreshToken) {
        try {
            CacheUtilities.write(REFRESH_TOKEN_FILENAME, refreshToken.getBytes(StandardCharsets.UTF_8));
            this.refreshToken = refreshToken;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String getAuthorisationURI() {
        try {
            URIBuilder authURIBuilder = new URIBuilder("https://accounts.spotify.com/authorize")
                    .setParameter("client_id", CLIENT_ID)
                    .setParameter("response_type", "code")
                    .setParameter("redirect_uri", REDIRECT_URI)
                    .setParameter("scope", "user-read-currently-playing user-read-playback-state");

            URI authURI = authURIBuilder.build();

            return authURI.toString();

        } catch (URISyntaxException e) {
            Logger.log("Invalid URI syntax in authorization URI.", e);
            Logger.println(e);
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            Logger.log("Failed to retrieve oAuth code from file.", e);
            Logger.println(e);
            throw new RuntimeException(e);
        }
    }

    private NewTokens startAuthorisationWorkflow() throws InterruptedException, IOException {
        SpotifyOAuthServer.startServer();
        Logger.println(getAuthorisationURI(), 1);
        String code = "";
        while (code.isEmpty()) {
            Thread.sleep(10000);
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
                            "&redirect_uri=" + REDIRECT_URI;

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + AUTHORISATION_STRING)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();


            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), Map.class);
            String accessToken = (String) tokenResponse.get("access_token");
            Instant accessTokenExpiry = Instant.now().plusMillis((Integer) tokenResponse.get("expires_in"));
            String refreshToken = (String) tokenResponse.get("refresh_token");
            Logger.println("Gotten new refresh token: " + refreshToken);
            return new NewTokens(accessToken, refreshToken, accessTokenExpiry);
        } catch (IOException | InterruptedException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private NewTokens refreshAccessToken() {
        try {
            URI tokenURI = URI.create("https://accounts.spotify.com/api/token");

            String body =
                    "grant_type=refresh_token" +
                            "&refresh_token=" + refreshToken;

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + AUTHORISATION_STRING)
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
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    };

    private String readOAuthCode() {
        try {
            return new String(CacheUtilities.read(OAUTH_FILENAME), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readRefreshToken() {
        try {
            return new String(CacheUtilities.read(REFRESH_TOKEN_FILENAME), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TokenManager getInstance() {
        TokenManager result = instance;
        if (result != null) {
            return result;
        }
        synchronized (TokenManager.class) {
            if (instance == null) {
                try {
                    instance = new TokenManager(HttpClient.newBuilder().build(), new ObjectMapper());
                } catch (InterruptedException | IOException e) {
                    throw new RuntimeException(e);
                }

            }
            return instance;
        }
    }

    public String getAccessToken() {
        synchronized (LOCK) {
            if (Instant.now().isAfter(accessTokenExpiry)) {
                NewTokens newTokens = refreshAccessToken();
                if (newTokens.accessToken() == null) {
                    throw new RuntimeException("token was null");
                }
                setAccessToken(newTokens.accessToken(), newTokens.expiry());
                setRefreshToken(newTokens.refreshToken());
            }
            return accessToken;
        }
    }
}

