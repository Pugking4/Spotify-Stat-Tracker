import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.HttpResponseException;

import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class SpotifyWrapper {
    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String accessToken;
    private static Instant accessTokenExpiry = Instant.now();
    private static String refreshToken;
    private static final String clientSecret = "73d7330795784cd68e64cfc770179c11";
    private static final String clientId = "c308214cd94247fdb57a48a98c3dfa7c";
    private static final String redirectUri = "http://127.0.0.1:8888/callback";
    private static final Path refreshTokenPath = Paths.get("./tracker/resources/refresh_token.txt");
    private static final Path authCodePath = Paths.get("./tracker/resources/oauth_code.txt");
    private static final String authorisationString = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

    static {
        if (retrieveRefreshToken().isEmpty()) {
            Logger.println("Couldn't find refresh token, starting full OAuth sequence.");
            try {
                authorise();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            refreshToken = retrieveRefreshToken();
            Logger.println("Found refresh token, using: " + refreshToken);
        }
    }

    private static String getAccessToken() {
        if (Instant.now().isAfter(accessTokenExpiry)) {
            accessTokenExpiry = Instant.now().plusSeconds(refreshAccessToken());
        }

        return accessToken;
    }

    private static long refreshAccessToken() {
        try {
            URI tokenURI = URI.create("https://accounts.spotify.com/api/token");

            String body =
                    "grant_type=refresh_token" +
                            "&refresh_token=" + refreshToken;

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + authorisationString)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();


            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), Map.class);

            if (tokenResponse.containsKey("error")) {
                Logger.println("Refresh token invalid, need full authorization: " + tokenResponse.get("error_description"));
                authorise();
                return 0;
            }

            if (tokenResponse.containsKey("refresh_token")) {
                refreshToken = (String) tokenResponse.get("refresh_token");
            }
            accessToken = (String) tokenResponse.get("access_token");
            return (Integer) tokenResponse.get("expires_in");

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void requestAccessToken(String code) {
        Logger.println("Requesting access token.");
        try {
            URI tokenURI = URI.create("https://accounts.spotify.com/api/token");

            String body =
                    "grant_type=authorization_code" +
                            "&code=" + code +
                            "&redirect_uri=" + redirectUri;

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + authorisationString)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();


            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), Map.class);
            accessToken = (String) tokenResponse.get("access_token");
            accessTokenExpiry = Instant.now().plusMillis((Integer) tokenResponse.get("expires_in"));
            refreshToken = (String) tokenResponse.get("refresh_token");
            Logger.println("Gotten new refresh token: " + refreshToken);

            Logger.println("Writing refresh token to file.");
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(refreshTokenPath))) {
                writer.println(refreshToken);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String retrieveOAuthCode() {
        String authCode = "";
        try {
            if (Files.exists(authCodePath) && Files.size(authCodePath) > 0) {
                List<String> lines = Files.readAllLines(authCodePath);
                if (!lines.isEmpty()) {
                    authCode = lines.getFirst().trim();
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file with Files.readAllLines: " + e.getMessage());
        }
        return authCode;
    }

    private static String retrieveRefreshToken() {
        try {
            if (Files.exists(refreshTokenPath) && Files.size(refreshTokenPath) > 0) {
                List<String> lines = Files.readAllLines(refreshTokenPath);
                if (!lines.isEmpty()) {
                    return lines.getFirst().trim();
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file with Files.readAllLines: " + e.getMessage());
        }
        return "";
    }


    private static void authorise() throws IOException {
        Logger.println("Starting OAuthServer.", 4);
        SpotifyOAuthServer.startServer();

        try {
            URIBuilder authURIBuilder = new URIBuilder("https://accounts.spotify.com/authorize")
                    .setParameter("client_id", "c308214cd94247fdb57a48a98c3dfa7c")
                    .setParameter("response_type", "code")
                    .setParameter("redirect_uri", "http://127.0.0.1:8888/callback")
                    .setParameter("scope", "user-read-currently-playing user-read-playback-state");

            URI authURI = authURIBuilder.build();

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(authURI);
            } else {
                Logger.println("Please open this URL in your browser: " + authURI.toString(), 1);
            }

            int attempts = 5;
            String code = retrieveOAuthCode();
            while (code.isEmpty() && attempts > 0) {
                Thread.sleep(1000);
                Logger.println("Starting attempt " + attempts + " to get oAuth code.");
                code = retrieveOAuthCode();
                attempts--;
            }

            if (attempts <= 0) throw new Exception("bruh");

            requestAccessToken(code);

        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> getCurrentlyPlayingTrack() throws HttpResponseException {
        try {
            URI currentPlayingURI = new URIBuilder("https://api.spotify.com/v1/me/player/currently-playing").build();
            HttpRequest currentPlayingRequest = HttpRequest.newBuilder(currentPlayingURI)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(currentPlayingRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> results;
            if (response.body().isEmpty()) {
                results = null;
            } else {
                results = objectMapper.readValue(response.body(), Map.class);
            }

            checkHTTPErrors(results);
            return results;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkHTTPErrors(Map<String, Object> map) throws HttpResponseException {
        if (map == null) throw new HttpResponseException(204, "HTTP response was empty.");
        if (!map.containsKey("error")) return;
        var errorMap = (Map<String, Object>) map.get("error");
        int statusCode = (int) errorMap.get("status");
        String message = (String) errorMap.get("message");

        throw new HttpResponseException(statusCode, message);

    }

    public static List<Map<String, Object>> getAvailableDevices() {
        try {
            URI availableDevicesURI = new URIBuilder("https://api.spotify.com/v1/me/player/devices").build();
            HttpRequest availableDevicesRequest = HttpRequest.newBuilder(availableDevicesURI)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(availableDevicesRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);

            // Get the "devices" list
            Object devicesObj = data.get("devices");
            if (devicesObj instanceof List<?> devicesList) {
                // Cast each element to Map<String,Object>
                return (List<Map<String, Object>>) (List<?>) devicesList;
            } else {
                return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
