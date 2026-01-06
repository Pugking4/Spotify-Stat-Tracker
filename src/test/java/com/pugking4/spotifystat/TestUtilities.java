package com.pugking4.spotifystat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.common.dto.*;
import com.pugking4.spotifystat.tracker.PlayingTrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class TestUtilities {
    public static String loadResource(String filename) {
        try (InputStream is = TestUtilities.class.getResourceAsStream("/" + filename)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Artist> getArtists(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(i -> {
                    Instant base = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60L);
                    return new Artist(
                            "artist-" + i,                 // id
                            "Artist " + i,                 // name
                            1000 + (i * 25),               // followers
                            List.of("genre" + (i % 6)),    // genres
                            "https://example.com/img/" + i + ".jpg", // image
                            i % 100,                       // popularity
                            base      // updatedAt
                    );
                })
                .toList();
    }

    public static List<Artist> getSkeletonArtists(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(i -> {
                    Instant base = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60L);
                    if (i % 2 == 0) base = null;
                    return new Artist(
                            "artist-" + i,                 // id
                            null,                 // name
                            null,               // followers
                            null,    // genres
                            null, // image
                            null,                       // popularity
                            base      // updatedAt
                    );
                })
                .toList();
    }

    public static PlayedTrack getPlayedTrack() {
        return new PlayedTrack(
                new Track(
                        "0ws53An61dr49WfNuxWNKM",
                        "UMASUGIRU",
                        new Album(
                                "24LeTf9xct196GYmJ7Qemo",
                                "UMASUGIRU",
                                "https://i.scdn.co/image/ab67616d0000b2733bc37da83f50aa7309cfe094",
                                LocalDate.of(2026, 1, 1),
                                "day",
                                "single",
                                List.of(
                                        new Artist(
                                                "4f2l5pSKd1oUMEMx7SZBng",
                                                "pinponpanpon",
                                                8795,
                                                List.of(
                                                        "hyperpop"
                                                ),
                                                "https://i.scdn.co/image/ab6761610000e5eb22281a4924e0b1f558026609",
                                                31,
                                                Instant.now().minusSeconds(14400)
                                        )
                                )
                        ),
                        155384,
                        false,
                        false,
                        List.of(
                                new Artist(
                                        "4f2l5pSKd1oUMEMx7SZBng",
                                        "pinponpanpon",
                                        8795,
                                        List.of(
                                                "hyperpop"
                                        ),
                                        "https://i.scdn.co/image/ab6761610000e5eb22281a4924e0b1f558026609",
                                        31,
                                        Instant.now().minusSeconds(14400)
                                )
                        )
                ),
                "collection",
                new Device(
                        "JOSHU-PC",
                        "Computer"
                ),
                0,
                Instant.now().minusSeconds(180)
        );
    }

    public static PlayingTrack getPlayingTrack(double percentageComplete) {
        return new PlayingTrack(
                "24LeTf9xct196GYmJ7Qemo",
                155384,
                (int) Math.round(155384 * percentageComplete)
        );
    }

    public static List<Map<String, Object>> getArtistsRaw(int amount) {
        Map<String, Object> base = new HashMap<>();
        base.put("id", "7DkhW1MaKKLwJTSC5TtVW3");
        base.put("name", "なるみや");
        base.put("type", "artist");
        base.put("popularity", 46);
        base.put("href", "https://api.spotify.com/v1/artists/7DkhW1MaKKLwJTSC5TtVW3?locale=en-US%2Cen%3Bq%3D0.5");
        base.put("uri", "spotify:artist:7DkhW1MaKKLwJTSC5TtVW3");

        base.put("external_urls", new HashMap<>(Map.of(
                "spotify", "https://open.spotify.com/artist/7DkhW1MaKKLwJTSC5TtVW3"
        )));

        Map<String, Object> followers = new HashMap<>();
        followers.put("href", null);
        followers.put("total", 41651);
        base.put("followers", followers);


        base.put("genres", new ArrayList<>(List.of("vocaloid")));

        List<Map<String, Object>> baseImages = new ArrayList<>();
        baseImages.add(new HashMap<>(Map.of("url", "https://i.scdn.co/image/ab6761610000e5eb42df248e509860e113728332", "height", 640, "width", 640)));
        baseImages.add(new HashMap<>(Map.of("url", "https://i.scdn.co/image/ab6761610000517442df248e509860e113728332", "height", 320, "width", 320)));
        baseImages.add(new HashMap<>(Map.of("url", "https://i.scdn.co/image/ab6761610000f17842df248e509860e113728332", "height", 160, "width", 160)));
        base.put("images", baseImages);

        List<Map<String, Object>> out = new ArrayList<>(amount);

        for (int i = 0; i < amount; i++) {
            String id = "test-artist-" + i;

            Map<String, Object> m = new HashMap<>(base); // shallow copy top-level

            // Replace scalars
            m.put("id", id);
            m.put("name", base.get("name") + "-" + i);
            m.put("uri", "spotify:artist:" + id);
            m.put("href", "https://api.spotify.com/v1/artists/" + id);

            // Replace nested maps/lists with fresh copies
            m.put("external_urls", new HashMap<>(Map.of(
                    "spotify", "https://open.spotify.com/artist/" + id
            )));

            Map<String, Object> baseFollowers = (Map<String, Object>) base.get("followers");
            m.put("followers", new HashMap<>(baseFollowers)); // ok: values are immutable/null

            m.put("genres", new ArrayList<>((List<String>) base.get("genres")));

            // Copy images list + each image map (so none are shared)
            List<Map<String, Object>> imagesCopy = new ArrayList<>();
            for (Map<String, Object> img : (List<Map<String, Object>>) base.get("images")) {
                imagesCopy.add(new HashMap<>(img));
            }
            m.put("images", imagesCopy);

            out.add(m);
        }

        return out;
    }


    public static Map<String, Object> getPlayingTrackFull(double percentageComplete) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> map = objectMapper.readValue(loadResource("currently-playing-miku.json"), Map.class);

        Map<String, Object> item = (Map<String, Object>) map.get("item");
        Number durationNum = (Number) item.get("duration_ms");
        int durationMs = durationNum.intValue();
        int progress = (int) Math.round(durationMs * percentageComplete);

        map.put("progress_ms", progress);
        return map;
    }


    public static List<Map<String, Object>> getDevicesFull() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        var results = objectMapper.readValue(loadResource("available-devices.json"), Map.class);
        return (List<Map<String, Object>>) results.getOrDefault("devices", List.of());
    }
}
