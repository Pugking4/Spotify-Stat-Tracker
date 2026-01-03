package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.PlayedTrack;
import com.pugking4.spotifystat.common.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class TrackingPoller {
    private static final int ACTIVE = 5;
    private static final int IDLE = 15;

    private volatile int delaySeconds = IDLE;
    private volatile boolean activeMode = false;

    private final SpotifyWrapper spotifyWrapper;
    private final DatabaseWrapper databaseWrapper;

    private PlayingTrack currentTrack;

    public TrackingPoller(SpotifyWrapper spotifyWrapper, DatabaseWrapper databaseWrapper) {
        this.spotifyWrapper = spotifyWrapper;
        this.databaseWrapper = databaseWrapper;
    }

    public ScheduledTaskSpecification spec() {
        return new ScheduledTaskSpecification(
                "Tracking Poller",
                this::run,
                DelayType.FIXED_RATE,
                Duration.ZERO,
                () -> Duration.ofSeconds(delaySeconds)
        );
    }

    private void run() {
        var trackData = poll();
        if (trackData.isEmpty()) return;
        setMode(trackData);
        handleTrackData(trackData);
    }

    private void handlePlayedTrack(Map<String, Object> trackData) {
        Logger.println("Track has finished playing.", 3);
        PlayedTrack playedTrack = createPlayedTrack(trackData, currentTrack);
        Logger.println("Calling DatabaseWrapper to record played track.", 4);
        databaseWrapper.insertPlayedTrack(playedTrack);
        Logger.println("DatabaseWrapper has been called.", 4);
        currentTrack = null;
    }

    private boolean isPlayingSong(Map<String, Object> data) {
        return (Boolean) data.get("is_playing");
    }

    private boolean isCurrentTrackPlaying(PlayingTrack playingTrack) {
        if (currentTrack == null) return false;
        return currentTrack.equals(playingTrack);
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

    private Map<String, Object> poll() {
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


    private void setActiveMode() {
        if (!activeMode) {
            activeMode = true;
            delaySeconds = ACTIVE;
        }
    }

    private void setIdleMode()   {
        if (activeMode)  {
            activeMode = false;
            delaySeconds = IDLE;
        }
    }

    private PlayedTrack createPlayedTrack(Map<String, Object> data, PlayingTrack playingTrack) {
        Logger.println("Starting.", 4);
        data.put("time_finished", playingTrack.timeFinished);
        return PlayedTrack.fromMap(data);
    }
}
