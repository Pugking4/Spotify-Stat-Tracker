package com.pugking4.spotifystat.tracker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.common.logging.Logger;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.HttpResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
public class SpotifyWrapper {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TokenManager tokenManager;
    private static final int MAX_ARTIST_BATCH_SIZE = 50;

    public SpotifyWrapper(HttpClient httpClient, ObjectMapper objectMapper, TokenManager tokenManager) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenManager = tokenManager;
    }

    public Map<String, Object> getCurrentlyPlayingTrack() {
        try {
            URI currentPlayingURI = new URIBuilder("https://api.spotify.com/v1/me/player/currently-playing").build();
            HttpRequest currentPlayingRequest = HttpRequest.newBuilder(currentPlayingURI)
                    .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                    .GET()
                    .build();
            Logger.println("Sending request.", 4);
            HttpResponse<String> response = httpClient.send(currentPlayingRequest, HttpResponse.BodyHandlers.ofString());
            Logger.println("Got response.", 4);
            Map<String, Object> results = objectMapper.readValue(response.body(), Map.class);

            Logger.println("Checking for errors.", 4);
            checkHTTPErrors(results, response.statusCode());
            Logger.println("No errors found.", 4);
            Logger.println("Returning results.", 4);
            return results;
        } catch (URISyntaxException | InterruptedException | IOException e) {
            throw new SpotifyApiException(-1, "Network failure: " + e.getMessage());
        }
    }

    private void checkHTTPErrors(Map<String, Object> map, int statusCode) {
        switch (statusCode) {
            case 200, 204 -> { return; }
            case 400, 401, 403, 404, 429 -> { }
            default -> throw new SpotifyApiException(statusCode, "Unexpected status: " + statusCode);
        }

        Map<String, Object> errorMap = (Map<String, Object>) map.get("error");
        String message = (String) errorMap.getOrDefault("message", "Unknown error");

        throw new SpotifyApiException(statusCode, message);
    }

    public List<Map<String, Object>> getAvailableDevices() {
        try {
            URI availableDevicesURI = new URIBuilder("https://api.spotify.com/v1/me/player/devices").build();
            HttpRequest availableDevicesRequest = HttpRequest.newBuilder(availableDevicesURI)
                    .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(availableDevicesRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
            checkHTTPErrors(data, response.statusCode());

            List<Map<String, Object>> devices = (List<Map<String, Object>>) data.getOrDefault("devices", List.of());

            return devices;
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new SpotifyApiException(-1, "Network failure: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getBatchArtists(List<String> ids) {
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
                    .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                    .GET()
                    .build();
            Logger.println("Sending request.", 4);
            HttpResponse<String> response = httpClient.send(artistsRequest, HttpResponse.BodyHandlers.ofString());
            Logger.println("Got response.", 4);
            Map<String, Object> results = objectMapper.readValue(response.body(), Map.class);
            Logger.println("Response is non empty.", 4);
            Logger.println(response.body(), 5);


            Logger.println("Checking for errors.", 4);
            checkHTTPErrors(results, response.statusCode());
            Logger.println("No errors found.", 4);

            List<Map<String, Object>> artists = (List<Map<String, Object>>) results.get("artists");
            for (Map<String, Object> artist : artists) {
                artist.put("updated_at", Instant.now());
            }

            Logger.println("Finished.", 4);
            return artists;
        } catch (URISyntaxException | InterruptedException | IOException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }
}

