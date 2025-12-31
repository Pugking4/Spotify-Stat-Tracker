package com.pugking4.spotifystat.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.common.logging.Logger;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.HttpResponseException;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
public class SpotifyWrapper {
    private static final HttpClient httpClient = HttpClient.newBuilder().build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String accessToken;
    private static Instant accessTokenExpiry = Instant.now();
    private static String refreshToken;
    static Dotenv dotenv = Dotenv.configure().directory(".").load();
    private static final String CLIENT_SECRET = dotenv.get("CLIENT_SECRET");
    private static final String CLIENT_ID = dotenv.get("CLIENT_ID");
    private static final String REDIRECT_URI = dotenv.get("REDIRECT_URI");
    private static final String REFRESH_TOKEN_FILENAME = "refresh_token.txt";
    private static final String OAUTH_FILENAME = "oauth_code.txt";
    private static final String AUTHORISATION_STRING = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    private static final int MAX_ARTIST_BATCH_SIZE = 50;

    static {
        if (retrieveRefreshToken().isEmpty()) {
            Logger.println("Couldn't find refresh token, starting full OAuth sequence.");
            // sleep until user visits oauthserver webpage, then run auth sequence
            authorise();
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
                    .header("Authorization", "Basic " + AUTHORISATION_STRING)
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

        } catch (IOException | InterruptedException e) {
            Logger.log("not sure", e);
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
                            "&redirect_uri=" + REDIRECT_URI;

            HttpRequest tokenRequest = HttpRequest.newBuilder(tokenURI)
                    .header("Authorization", "Basic " + AUTHORISATION_STRING)
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
            CacheUtilities.write(REFRESH_TOKEN_FILENAME, refreshToken.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static String retrieveOAuthCode() {
        try {
            Path oAuthPath = CacheUtilities.getAbsolutePath(OAUTH_FILENAME);
            Logger.println("Got path: " +  oAuthPath.toString(), 3);
            if (Files.exists(oAuthPath) && Files.size(oAuthPath) > 0) {
                Logger.println("File exists!", 2);
                return new String(CacheUtilities.read(OAUTH_FILENAME), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("Error reading file with Files.readAllLines: " + e.getMessage());
        }
        Logger.println("File doesnt exist...", 1);
        return "";
    }

    private static String retrieveRefreshToken() {
        try {
            Path refreshTokenPath = CacheUtilities.getAbsolutePath(REFRESH_TOKEN_FILENAME);
            if (Files.exists(refreshTokenPath) && Files.size(refreshTokenPath) > 0) {
                return new String(CacheUtilities.read(REFRESH_TOKEN_FILENAME), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
        return "";
    }


    private static void authorise() {
        Logger.println("Starting OAuthServer.", 4);
        SpotifyOAuthServer.startServer();

        try {
            URIBuilder authURIBuilder = new URIBuilder("https://accounts.spotify.com/authorize")
                    .setParameter("client_id", CLIENT_ID)
                    .setParameter("response_type", "code")
                    .setParameter("redirect_uri", REDIRECT_URI)
                    .setParameter("scope", "user-read-currently-playing user-read-playback-state");

            URI authURI = authURIBuilder.build();

            Logger.println("Please open this URL in your browser: " + authURI.toString(), 1);

            int attempts = 5;
            String code = retrieveOAuthCode();
            while (code.isEmpty() && attempts > 0) {
                Thread.sleep(10000);
                Logger.println("Starting attempt " + attempts + " to get oAuth code.");
                code = retrieveOAuthCode();
                attempts--;
            }

            if (attempts <= 0) throw new IllegalStateException("Failed to retrieve oAuth code from file.");

            requestAccessToken(code);

        } catch (URISyntaxException e) {
            Logger.log("Invalid URI syntax in authorization URI.", e);
            Logger.println(e);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Logger.log("Thread interrupted while waiting for OAuth code.", e);
            Logger.println(e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (IllegalStateException e) {
            Logger.log("Failed to retrieve oAuth code from file.", e);
            Logger.println(e);
            throw new RuntimeException(e);
        } /*catch (IOException e) {
            Logger.log("Failed to launch browser.", e);
            Logger.println(e);
            throw new RuntimeException(e);
        }*/
    }

    public static Map<String, Object> getCurrentlyPlayingTrack() throws HttpResponseException {
        try {
            URI currentPlayingURI = new URIBuilder("https://api.spotify.com/v1/me/player/currently-playing").build();
            HttpRequest currentPlayingRequest = HttpRequest.newBuilder(currentPlayingURI)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .build();
            Logger.println("Sending request.", 4);
            HttpResponse<String> response = httpClient.send(currentPlayingRequest, HttpResponse.BodyHandlers.ofString());
            Logger.println("Got response.", 4);
            Map<String, Object> results;
            if (response.statusCode() == 204) {
                Logger.println("Response is empty, code 204.", 4);
                return null;
            } else {
                results = objectMapper.readValue(response.body(), Map.class);
                Logger.println("Response is non empty.", 4);
                Logger.println(response.body(), 5);
            }


            Logger.println("Checking for errors.", 4);
            checkHTTPErrors(results, response.statusCode());
            Logger.println("No errors found.", 4);
            Logger.println("Returning results.", 4);
            return results;
        } catch (HttpResponseException e) {
            throw e;
        } catch (URISyntaxException | InterruptedException | IOException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static void checkHTTPErrors(Map<String, Object> map, int statusCode) throws HttpResponseException {
        if (map == null) {
            Logger.println("Map was null.", 2);
            throw new RuntimeException();
        }
        if (!map.containsKey("error")) return;
        var errorMap = (Map<String, Object>) map.get("error");
        String message = (String) errorMap.get("message");

        throw new HttpResponseException(statusCode, message);
    }

    public static List<Map<String, Object>> getAvailableDevices() throws HttpResponseException {
        try {
            URI availableDevicesURI = new URIBuilder("https://api.spotify.com/v1/me/player/devices").build();
            HttpRequest availableDevicesRequest = HttpRequest.newBuilder(availableDevicesURI)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(availableDevicesRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);

            Object devicesObj = data.get("devices");
            if (devicesObj instanceof List<?> devicesList) {
                return (List<Map<String, Object>>) devicesList;
            } else {
                return null;
            }

        } catch (HttpResponseException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.log("not sure", e);
        }

        return null;
    }

    public static List<Artist> getBatchArtists(List<String> ids) {
        StringBuilder idArgument = new StringBuilder();
        for (int i = 0; i < Math.clamp(ids.size(), 0, MAX_ARTIST_BATCH_SIZE); i++) {
            idArgument.append(ids.get(i)).append(",");
        }
        idArgument.deleteCharAt(idArgument.length() - 1);

        try {
            URI artistsURI = new URIBuilder("https://api.spotify.com/v1/artists")
                    .setParameter("ids", idArgument.toString())
                    .build();
            HttpRequest artistsRequest = HttpRequest.newBuilder(artistsURI)
                    .header("Authorization", "Bearer " + getAccessToken())
                    .GET()
                    .build();
            Logger.println("Sending request.", 4);
            HttpResponse<String> response = httpClient.send(artistsRequest, HttpResponse.BodyHandlers.ofString());
            Logger.println("Got response.", 4);
            Map<String, Object> results;
            if (response.body().isEmpty()) {
                results = null;
                Logger.println("Response is empty.", 4);
            } else {
                results = objectMapper.readValue(response.body(), Map.class);
                Logger.println("Response is non empty.", 4);
                Logger.println(response.body(), 5);
            }


            Logger.println("Checking for errors.", 4);
            checkHTTPErrors(results, response.statusCode());
            Logger.println("No errors found.", 4);

            List<Artist> newArtists = new ArrayList<>();
            List<Map<String, Object>> artists = (List<Map<String, Object>>) results.get("artists");
            for (Map<String, Object> artist : artists) {
                artist.put("updated_at", Instant.now());
                newArtists.add(Artist.fromMap(artist));
            }

            Logger.println("Finished.", 4);
            return newArtists;
        } catch (URISyntaxException | InterruptedException | IOException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }
}
