package com.pugking4.spotifystat.updater;

/*

Run updates every 24hr (make 24 - 72hrs if exceeding rate limit, doubt ill need to, need to exceed 288,000 artists in db)

*/

import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.tracker.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScheduledUpdater {
    private static final ScheduledExecutorService executor;
    private static final int UPDATE_PERIOD_SECONDS = 30;
    private static final int HIGH_UPDATE_WAIT_SECONDS = 72 * 60 * 60;
    private static final int MID_UPDATE_WAIT_SECONDS = 24 * 60 * 60;
    private static final int LOW_UPDATE_WAIT_SECONDS = 12 * 60 * 60;

    static {
        Logger.println("Preparing to create scheduled updater...", 4);
        executor = Executors.newScheduledThreadPool(1);
        Logger.println("Created executor...", 4);
        Runnable myTask = new Runnable() {
            @Override
            public void run() {
                Logger.println("Starting artist update...", 4);
                List<Artist> artists = DatabaseWrapper.getArtists();
                Logger.println("Got " + artists.size() + " artists from database.", 4);
                List<String> artistUpdateList = artists.stream()
                        .map(a -> new Pair<String, Priority>(a.id(), getPriority(a.updatedAt())))
                        .filter(p -> !p.right().equals(Priority.DO_NOT_UPDATE))
                        .sorted(Comparator.comparing(Pair::right))
                        .map(Pair::left)
                        .toList();
                Logger.println(artistUpdateList.size() + " artists scheduled for updating.", 4);
                if (artistUpdateList.isEmpty()) return;
                List<Artist> updatedArtists = SpotifyWrapper.getBatchArtists(artistUpdateList);
                Logger.println(updatedArtists.size() + " Got updated artist data.", 4);
                DatabaseWrapper.updateBatchArtists(updatedArtists);
                Logger.println(artistUpdateList.size() + " Wrote updated data.", 4);
                Logger.println("Finished artist update.", 4);
            }
        };
        Logger.println("Created task...", 4);

        executor.scheduleAtFixedRate(myTask, 0, UPDATE_PERIOD_SECONDS, TimeUnit.SECONDS);
        Logger.println("Assigned task...", 4);
        Logger.println("Finished.", 4);
    }

    public static void start() {}

    private static Priority getPriority(Instant updatedAt) {
        if (updatedAt == null) {
            return Priority.MAX;
        } else if (updatedAt.isBefore(Instant.now().minusSeconds(HIGH_UPDATE_WAIT_SECONDS))) {
            Logger.println("HIGH PRIORITY!", 5);
            return Priority.HIGH;
        } else if (updatedAt.isBefore(Instant.now().minusSeconds(MID_UPDATE_WAIT_SECONDS))) {
            Logger.println("MID PRIORITY.", 5);
            return Priority.MEDIUM;
        } else if (updatedAt.isBefore(Instant.now().minusSeconds(LOW_UPDATE_WAIT_SECONDS))) {
            Logger.println("LOW PRIORITY.", 5);
            return Priority.LOW;
        } else {
            Logger.println("DO NOT UPDATE!", 5);
            return Priority.DO_NOT_UPDATE;
        }
    }

    public enum Priority {
        MAX,
        HIGH,
        MEDIUM,
        LOW,
        DO_NOT_UPDATE
    }
}
