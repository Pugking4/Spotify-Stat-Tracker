package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.common.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ArtistUpdater {
    private final int delaySeconds = 30;

    private final SpotifyWrapper spotifyWrapper;
    private final DatabaseWrapper databaseWrapper;
    private final PriorityClassifier priorityClassifier;



    public ArtistUpdater(SpotifyWrapper spotifyWrapper, DatabaseWrapper databaseWrapper, PriorityClassifier priorityClassifier) {
        this.spotifyWrapper = spotifyWrapper;
        this.databaseWrapper = databaseWrapper;
        this.priorityClassifier = priorityClassifier;
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
        List<Artist> skeletonArtists = databaseWrapper.getAllSkeletonArtists();
        var artistIDs = computeUpdateCandidates(skeletonArtists);
        if (artistIDs.isEmpty()) return;
        List<Map<String, Object>> updatedArtistsRaw = spotifyWrapper.getBatchArtists(artistIDs);
        List<Artist> updatedArtists = updatedArtistsRaw.stream().map(Artist::fromMap).toList();
        databaseWrapper.updateBatchArtists(updatedArtists);
    }

    List<String> computeUpdateCandidates(List<Artist> artists) {
        return artists.stream()
                .map(a -> new Pair<String, Priority>(a.id(), priorityClassifier.classify(a.updatedAt())))
                .filter(p -> p.right() != Priority.DO_NOT_UPDATE)
                .sorted(Comparator.comparing(Pair::right))
                .map(Pair::left)
                .toList();
    }
}
