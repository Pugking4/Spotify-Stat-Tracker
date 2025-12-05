package com.pugking4.spotifystat.tracker;

import java.time.Instant;
import java.util.Map;

public class PlayingTrack {
    public String id;
    public Integer durationMs;
    public Integer progressMs;
    private Integer startedMs;
    public Instant timeFinished;
    public Boolean played;

    public PlayingTrack(String id, Integer durationMs, Integer progressMs) {
        this.id = id;
        this.durationMs = durationMs;
        this.progressMs = progressMs;
        this.startedMs = progressMs;
        this.played = false;
    }

    public void updateProgress(int progressMs) {
        if (progressMs < startedMs) {
            startedMs = progressMs;
        }
        if ((double) (progressMs - startedMs) / durationMs >= 0.7) {
            played = true;
            timeFinished = Instant.now();
        }

        this.progressMs = progressMs;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PlayingTrack pt) {
            return pt.id.equals(this.id);
        } else {
            return false;
        }
    }

    public static PlayingTrack fromMap(Map<String, Object> map) {
        Integer progressMs = (Integer) map.get("progress_ms");

        Map<String, Object> itemMap = (Map<String, Object>) map.get("item");
        String trackId = (String) itemMap.get("id");
        Integer durationMs = (Integer) itemMap.get("duration_ms");

        return new PlayingTrack(trackId, durationMs, progressMs);
    }
}
