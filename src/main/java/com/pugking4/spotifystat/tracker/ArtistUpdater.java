package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.common.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ArtistUpdater {
    private static final int HIGH_DELAY = 72 * 60 * 60;
    private static final int MID_DELAY = 24 * 60 * 60;
    private static final int LOW_DELAY = 12 * 60 * 60;

    private final int delaySeconds = 30;

    private final SpotifyWrapper spotifyWrapper;



    public ArtistUpdater(SpotifyWrapper spotifyWrapper) {
        this.spotifyWrapper = spotifyWrapper;
    }

    public ScheduledTaskSpecification spec() {
        return new ScheduledTaskSpecification(
                "Artist Updater",
                this::run,
                DelayType.FIXED_DELAY,
                Duration.ZERO,
                () -> Duration.ofSeconds(delaySeconds)
        );
    }

    private void run() {
        List<Artist> artists = DatabaseWrapper.getArtists();
        List<String> artistUpdateList = artists.stream()
                .map(a -> new Pair<String, Priority>(a.id(), getPriority(a.updatedAt())))
                .filter(p -> !p.right().equals(Priority.DO_NOT_UPDATE))
                .sorted(Comparator.comparing(Pair::right))
                .map(Pair::left)
                .toList();
        if (artistUpdateList.isEmpty()) return;
        List<Map<String, Object>> updatedArtistsRaw = spotifyWrapper.getBatchArtists(artistUpdateList);
        List<Artist> updatedArtists = updatedArtistsRaw.stream().map(Artist::fromMap).toList();
        DatabaseWrapper.updateBatchArtists(updatedArtists);
    }

    private Priority getPriority(Instant updatedAt) {
        if (updatedAt == null) {
            return Priority.MAX;
        } else if (updatedAt.isBefore(Instant.now().minusSeconds(HIGH_DELAY))) {
            Logger.println("HIGH PRIORITY!", 5);
            return Priority.HIGH;
        } else if (updatedAt.isBefore(Instant.now().minusSeconds(MID_DELAY))) {
            Logger.println("MID PRIORITY.", 5);
            return Priority.MEDIUM;
        } else if (updatedAt.isBefore(Instant.now().minusSeconds(LOW_DELAY))) {
            Logger.println("LOW PRIORITY.", 5);
            return Priority.LOW;
        } else {
            //Logger.println("DO NOT UPDATE!", 5);
            return Priority.DO_NOT_UPDATE;
        }
    }
}
