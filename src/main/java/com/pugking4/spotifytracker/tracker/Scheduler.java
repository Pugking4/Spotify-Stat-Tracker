package com.pugking4.spotifytracker.tracker;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.pugking4.spotifytracker.dto.*;
import org.apache.http.client.HttpResponseException;

public class Scheduler {
    private final ScheduledExecutorService executor;
    private final int pollingIntervalActiveSeconds = 5;
    private final int pollingIntervalIdleSeconds = 15;
    private boolean activeMode;
    private final Runnable myTask;
    private ScheduledFuture<?> futureTask;

    private PlayingTrack currentTrack;

    public Scheduler() {
        this.executor = Executors.newScheduledThreadPool(2);
        this.activeMode = false;
        myTask = new Runnable() {
            @Override
            public void run() {
                Logger.println("Starting poll.", 4);
                Map<String, Object> trackData = null;
                try {
                    Logger.println("Trying to get currently playing track.", 4);
                    trackData = SpotifyWrapper.getCurrentlyPlayingTrack();
                    Logger.println("Successfully got current track.", 4);
                } catch (HttpResponseException e) {
                    Logger.println("Failed to get current track.", 4);
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
                List<Map<String, Object>> deviceData = SpotifyWrapper.getAvailableDevices();
                if (deviceData == null) return;
                Map<String, Object> deviceRaw = deviceData.stream().filter(x -> (Boolean) x.get("is_active")).findFirst().get();
                assert trackData != null;
                trackData.put("device", deviceRaw);

                if (isPlayingSong(trackData)) {
                    Logger.println("Song is playing.", 4);
                    setActiveMode();
                    PlayingTrack playingTrack = createPlayingTrack(trackData);
                    if (isCurrentTrackPlaying(playingTrack)) {
                        Logger.println("Current track is still playing.", 4);
                        currentTrack.updateProgress(playingTrack.progressMs);
                        if (currentTrack.played) {
                            Logger.println("Track has finished playing.", 3);
                            PlayedTrack playedTrack = createPlayedTrack(trackData, currentTrack);
                            Logger.println("Calling DatabaseWrapper to record played track.", 4);
                            executor.submit(() -> {
                                try {
                                    DatabaseWrapper.insertPlayedTrack(playedTrack);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                            Logger.println("DatabaseWrapper has been called.", 4);
                            currentTrack = null;
                        }
                    } else {
                        Logger.println("New track has started playing.", 3);
                        currentTrack = playingTrack;
                    }
                } else {
                    Logger.println("No song is playing.", 4);
                    setIdleMode();
                }
                Logger.println("Ending poll.", 4);
            }
        };

        futureTask = executor.scheduleAtFixedRate(myTask, 0, pollingIntervalIdleSeconds, TimeUnit.SECONDS);
    }

    private boolean isCurrentTrackPlaying(PlayingTrack playingTrack) {
        if (currentTrack == null) return false;
        return currentTrack.equals(playingTrack);
    }

    private boolean isPlayingSong(Map<String, Object> data) {
        return (Boolean) data.get("is_playing");
    }

    private PlayingTrack createPlayingTrack(Map<String, Object> data) {
        Integer progressMs = (Integer) data.get("progress_ms");

        Map<String, Object> itemMap = (Map<String, Object>) data.get("item");
        String trackId = (String) itemMap.get("id");
        Integer durationMs = (Integer) itemMap.get("duration_ms");

        PlayingTrack playingTrack = new PlayingTrack(trackId, durationMs, progressMs);
        return playingTrack;
    }

    private void setActiveMode() {
        if (activeMode) return;
        activeMode = true;
        changeReadInterval(pollingIntervalActiveSeconds);
    }

    private void setIdleMode() {
        if (!activeMode) return;
        activeMode = false;
        changeReadInterval(pollingIntervalIdleSeconds);
    }

    private void changeReadInterval(long time)
    {
        if(time > 0)
        {
            if (futureTask != null)
            {
                futureTask.cancel(true);
            }

            futureTask = executor.scheduleAtFixedRate(myTask, 0, time, TimeUnit.SECONDS);
        }
    }

    private PlayedTrack createPlayedTrack(Map<String, Object> data, PlayingTrack playingTrack) {
        Logger.println("Starting.", 4);
        String repeatState = (String) data.get("repeat_state");
        Boolean shuffleState = (Boolean) data.get("shuffle_state");
        Instant timeFinished = playingTrack.timeFinished;

        Map<String, Object> deviceMap = (Map<String, Object>) data.get("device");
        Device device = new Device((String) deviceMap.get("name"), (String) deviceMap.get("type"));

        Map<String, Object> contextMap = (Map<String, Object>) data.get("context");
        String contextType = (String) contextMap.get("type");

        Map<String, Object> itemMap = (Map<String, Object>) data.get("item");

        Integer durationMs = (Integer) itemMap.get("duration_ms");
        Boolean isExplicit = (Boolean) itemMap.get("explicit");
        Boolean isLocal = (Boolean) itemMap.get("is_local");
        String trackId = (String) itemMap.get("id");
        String trackName = (String) itemMap.get("name");
        Integer currentPopularity = (Integer) itemMap.get("popularity");

        List<Artist> trackArtists = new ArrayList<>();
        List<Map<String, Object>> trackArtistMaps = (List<Map<String, Object>>) itemMap.get("artists");
        for (Map<String, Object> trackArtistMap : trackArtistMaps) {
            String trackArtistId = (String) trackArtistMap.get("id");
            String trackArtistName = (String) trackArtistMap.get("name");
            Artist trackArtist = new Artist(trackArtistId, trackArtistName);
            trackArtists.add(trackArtist);
        }

        Map<String, Object> albumMap = (Map<String, Object>) itemMap.get("album");
        String albumId = (String) albumMap.get("id");
        String albumType = (String) albumMap.get("album_type");
        String albumName = (String) albumMap.get("name");

        String albumReleaseDateRaw = (String) albumMap.get("release_date");
        String albumReleaseDatePrecision = (String) albumMap.get("release_date_precision");

        LocalDate albumReleaseDate = parseSpotifyDate(albumReleaseDateRaw, albumReleaseDatePrecision);


        List<Artist> albumArtists = new ArrayList<>();
        List<Map<String, Object>> albumArtistMaps = (List<Map<String, Object>>) albumMap.get("artists");
        for (Map<String, Object> albumArtistMap : albumArtistMaps) {
            String albumArtistId = (String) albumArtistMap.get("id");
            String albumArtistName = (String) albumArtistMap.get("name");
            Artist albumArtist = new Artist(albumArtistId, albumArtistName);
            albumArtists.add(albumArtist);
        }

        List<Map<String, Object>> imageMaps = (List<Map<String, Object>>) albumMap.get("images");
        String albumCover = (String) imageMaps.getFirst().get("url");

        Album album = new Album(albumId, albumName, albumCover, albumReleaseDate, albumReleaseDatePrecision, albumType, albumArtists);
        Track track = new Track(trackId, trackName, album, durationMs, isExplicit, isLocal, trackArtists);

        PlayedTrack playedTrack = new PlayedTrack(track,contextType, device, currentPopularity, timeFinished);
        Logger.println("Ending.", 4);
        return playedTrack;
    }

    private static LocalDate parseSpotifyDate(String date, String precision) {
        return switch (precision) {
            case "year" -> LocalDate.of(Integer.parseInt(date), 1, 1);
            case "month" -> {
                String[] parts = date.split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                yield LocalDate.of(year, month, 1);
            }
            case "day" -> LocalDate.parse(date); // full YYYY-MM-DD
            default -> throw new IllegalArgumentException("Unknown precision: " + precision);
        };
    }

    public void stop() {
        if (futureTask != null && !futureTask.isCancelled()) {
            futureTask.cancel(true); // interrupts the running task
        }

        executor.shutdownNow(); // stops the executor and any remaining tasks
    }


}
