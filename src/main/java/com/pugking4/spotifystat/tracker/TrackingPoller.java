package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.PlayedTrack;
import com.pugking4.spotifystat.common.logging.Logger;
import org.apache.http.client.HttpResponseException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

public class TrackingPoller implements ScheduledTask {
    private ScheduledFuture<?> futureTask;
    private final int POLLING_INTERVAL_ACTIVE_SECONDS = 5;
    private final int POLLING_INTERVAL_IDLE_SECONDS = 15;
    private PlayingTrack currentTrack;
    private int delay = POLLING_INTERVAL_IDLE_SECONDS;
    private boolean isPendingIntervalChange = false;
    private Runnable wrappedTask;
    private boolean activeMode = false;
    private boolean isFirstRun = true;
    final private Runnable task;
    private final SpotifyWrapper spotifyWrapper;

    public TrackingPoller(SpotifyWrapper spotifyWrapper) {
        this.spotifyWrapper = spotifyWrapper;

        task = new Runnable() {
            @Override
            public void run() {
                Map<String, Object> trackData = null;
                try {
                    trackData = poll();
                } catch (HttpResponseException e) {
                    throw new RuntimeException(e);
                }
                if (trackData.isEmpty()) return;
                setMode(trackData);
                handleTrackData(trackData);
                isFirstRun = false;
            }
        };
    }

    private void handlePlayedTrack(Map<String, Object> trackData) {
        Logger.println("Track has finished playing.", 3);
        PlayedTrack playedTrack = createPlayedTrack(trackData, currentTrack);
        Logger.println("Calling DatabaseWrapper to record played track.", 4);
        DatabaseWrapper.insertPlayedTrack(playedTrack);
                        /*executor.submit(() -> {
                            try {
                                DatabaseWrapper.insertPlayedTrack(playedTrack);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });*/

        Logger.println("DatabaseWrapper has been called.", 4);
        currentTrack = null;
    }

    private void handleTrackData(Map<String, Object> trackData) {
        if (isPlayingSong(trackData)) {
            Logger.println("Song is playing.", 4);
            PlayingTrack playingTrack = PlayingTrack.fromMap(trackData);
            if (isCurrentTrackPlaying(playingTrack)) {
                Logger.println("Current track is still playing.", 4);
                currentTrack.updateProgress(playingTrack.progressMs);
                if (currentTrack.played) {
                    handlePlayedTrack(trackData);
                }
            } else {
                Logger.println("New track has started playing.", 3);
                currentTrack = playingTrack;
            }
        } else {
            Logger.println("No song is playing.", 4);
        }
    }

    private void setMode(Map<String, Object> trackData) {
        if (isPlayingSong(trackData)) {
            setActiveMode();
        } else {
            setIdleMode();
        }
    }

    private Map<String, Object> poll() throws HttpResponseException {
        Logger.println("Starting poll.", 4);
        Map<String, Object> trackData = spotifyWrapper.getCurrentlyPlayingTrack();
        assert trackData != null;
        if (!trackData.isEmpty()) {
            List<Map<String, Object>> devices = spotifyWrapper.getAvailableDevices();
            Map<String, Object> activeDevice = devices.stream().filter(x -> (Boolean) x.get("is_active")).findFirst().get();
            trackData.put("device", activeDevice);
        }
        Logger.println("Ending poll.", 4);
        return trackData;
    }

    private void handleHttpResponseException(HttpResponseException e) {
        switch (e.getStatusCode()) {
            case 204 -> {
                Logger.println("Nothing is playing, empty response: " + e.getMessage(), 4);
                return;
            }
            case 401 -> {
                Logger.println("SpotifyWrapper needs to be reauthenticated: " + e.getMessage(), 1);
                Logger.log("HTTP response failed", e);
                return;
            }
            case 403 -> {
                Logger.log("HTTP response failed", e);
                return;
            }
            case 429 -> {
                Logger.println("Rate limit has been reached: " + e.getMessage(), 1);
                Logger.log("HTTP response failed", e);
                return;
            }
        }
    }


    @Override
    public int getRequiredNumberOfThreads() {
        return 2;
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
        return DelayType.FIXED_RATE;
    }

    @Override
    public boolean isPendingDelayChange() {
        return isPendingIntervalChange;
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
    public void resolvePendingDelayChange() {
        isPendingIntervalChange = false;
    }

    private boolean isCurrentTrackPlaying(PlayingTrack playingTrack) {
        if (currentTrack == null) return false;
        return currentTrack.equals(playingTrack);
    }

    private boolean isPlayingSong(Map<String, Object> data) {
        return (Boolean) data.get("is_playing");
    }

    private void setActiveMode() {
        if (activeMode) return;
        activeMode = true;
        delay = POLLING_INTERVAL_ACTIVE_SECONDS;
        isPendingIntervalChange = true;
    }

    private void setIdleMode() {
        if (!activeMode) return;
        activeMode = false;
        delay = POLLING_INTERVAL_IDLE_SECONDS;
        isPendingIntervalChange = true;
    }

    private PlayedTrack createPlayedTrack(Map<String, Object> data, PlayingTrack playingTrack) {
        Logger.println("Starting.", 4);
        data.put("time_finished", playingTrack.timeFinished);
        return PlayedTrack.fromMap(data);
    }
}
