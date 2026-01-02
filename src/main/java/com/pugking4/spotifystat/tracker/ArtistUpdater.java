package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.common.dto.PlayedTrack;
import com.pugking4.spotifystat.common.logging.Logger;
import org.apache.http.client.HttpResponseException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ArtistUpdater implements ScheduledTask {
    private ScheduledFuture<?> futureTask;
    private int delay = 30;
    private Runnable wrappedTask;
    private boolean isFirstRun = true;
    final private Runnable task;
    private final SpotifyWrapper spotifyWrapper;

    private final int HIGH_UPDATE_WAIT_SECONDS = 72 * 60 * 60;
    private final int MID_UPDATE_WAIT_SECONDS = 24 * 60 * 60;
    private final int LOW_UPDATE_WAIT_SECONDS = 12 * 60 * 60;

    public ArtistUpdater(SpotifyWrapper spotifyWrapper) {
        this.spotifyWrapper = spotifyWrapper;

        task = new Runnable() {
            @Override
            public void run() {
                //Logger.println("Preparing to create scheduled updater...", 4);
                //Logger.println("Created executor...", 4);
                //Logger.println("Starting artist update...", 4);
                List<Artist> artists = DatabaseWrapper.getArtists();
                //Logger.println("Got " + artists.size() + " artists from database.", 4);
                List<String> artistUpdateList = artists.stream()
                        .map(a -> new Pair<String, Priority>(a.id(), getPriority(a.updatedAt())))
                        .filter(p -> !p.right().equals(Priority.DO_NOT_UPDATE))
                        .sorted(Comparator.comparing(Pair::right))
                        .map(Pair::left)
                        .toList();
                //Logger.println(artistUpdateList.size() + " artists scheduled for updating.", 4);
                if (artistUpdateList.isEmpty()) return;
                List<Map<String, Object>> updatedArtistsRaw = spotifyWrapper.getBatchArtists(artistUpdateList);
                List<Artist> updatedArtists = updatedArtistsRaw.stream().map(Artist::fromMap).toList();
                //Logger.println(updatedArtists.size() + " Got updated artist data.", 4);
                DatabaseWrapper.updateBatchArtists(updatedArtists);
                //Logger.println(artistUpdateList.size() + " Wrote updated data.", 4);
                //Logger.println("Finished artist update.", 4);

                //Logger.println("Finished.", 4);
                isFirstRun = false;
            }
        };
    }

    @Override
    public int getRequiredNumberOfThreads() {
        return 1;
    }

    @Override
    public Runnable getTask() {
        return task;
    }

    @Override
    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
        this.futureTask = scheduledFuture;
    }

    @Override
    public ScheduledFuture<?> getScheduledFuture() {
        return futureTask;
    }

    @Override
    public int getDelay() {
        return delay;
    }

    @Override
    public int getInitialDelay() {
        return 0;
    }

    @Override
    public DelayType getDelayType() {
        return DelayType.FIXED_DELAY;
    }

    @Override
    public boolean isPendingDelayChange() {
        return false;
    }

    @Override
    public void setWrappedTask(Runnable wrappedTask) {
        this.wrappedTask = wrappedTask;
    }

    @Override
    public Runnable getWrappedTask() {
        return wrappedTask;
    }

    @Override
    public boolean isFirstRun() {
        return isFirstRun;
    }

    @Override
    public void resolvePendingDelayChange() {}

    private Priority getPriority(Instant updatedAt) {
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
            //Logger.println("DO NOT UPDATE!", 5);
            return Priority.DO_NOT_UPDATE;
        }
    }
}
