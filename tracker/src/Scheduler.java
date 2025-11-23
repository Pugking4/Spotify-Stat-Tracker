import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
                System.out.println("Scheduler: Starting poll.");
                Map<String, Object> trackData = SpotifyWrapper.getCurrentlyPlayingTrack();
                if (trackData == null) return;
                List<Map<String, Object>> deviceData = SpotifyWrapper.getAvailableDevices();
                if (deviceData == null) return;
                Map<String, Object> deviceRaw = deviceData.stream().filter(x -> (Boolean) x.get("is_active")).findFirst().get();
                trackData.put("device", deviceRaw);

                if (isPlayingSong(trackData)) {
                    System.out.println("Scheduler: Song is playing.");
                    setActiveMode();
                    PlayingTrack playingTrack = createPlayingTrack(trackData);
                    if (isCurrentTrackPlaying(playingTrack)) {
                        System.out.println("Scheduler: Current track is still playing.");
                        currentTrack.updateProgress(playingTrack.progressMs);
                        if (currentTrack.played) {
                            System.out.println("Scheduler: Track has finished playing.");
                            PlayedTrack playedTrack = createPlayedTrack(trackData, currentTrack);
                            //DatabaseWrapper.insertPlayedTrack(playedTrack);
                            System.out.println("Scheduler: Calling DatabaseWrapper to record played track.");
                            executor.submit(() -> {
                                try {
                                    DatabaseWrapper.insertPlayedTrack(playedTrack);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                            System.out.println("Scheduler: DatabaseWrapper has been called.");
                            currentTrack = null;
                        }
                    } else {
                        System.out.println("Scheduler: New track has started playing.");
                        currentTrack = playingTrack;
                    }
                } else {
                    System.out.println("Scheduler: No song is playing.");
                    setIdleMode();
                }
                System.out.println("Scheduler: Ending poll.");
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
        /*System.out.println("DEBUG!");

        for (var thing : data.keySet()) {
            System.out.println(thing + ": " + data.get(thing).getClass());
        }

        System.out.println("!GUBED");*/


        System.out.println("Scheduler, createPlayedTrack: Starting.");
        String repeatState = (String) data.get("repeat_state");
        Boolean shuffleState = (Boolean) data.get("shuffle_state");
        Instant timeFinished = playingTrack.timeFinished;
        //System.out.println("Scheduler, createPlayedTrack: 1.");

        Map<String, Object> deviceMap = (Map<String, Object>) data.get("device");
        Device device = new Device((String) deviceMap.get("name"), (String) deviceMap.get("type"));
        //System.out.println("Scheduler, createPlayedTrack: 2.");

        Map<String, Object> contextMap = (Map<String, Object>) data.get("context");
        String contextType = (String) contextMap.get("type");
        //System.out.println("Scheduler, createPlayedTrack: 3.");

        Map<String, Object> itemMap = (Map<String, Object>) data.get("item");
        //System.out.println("Scheduler, createPlayedTrack: 4.");

        Integer durationMs = (Integer) itemMap.get("duration_ms");
        Boolean isExplicit = (Boolean) itemMap.get("explicit");
        Boolean isLocal = (Boolean) itemMap.get("is_local");
        String trackId = (String) itemMap.get("id");
        String trackName = (String) itemMap.get("name");
        Integer currentPopularity = (Integer) itemMap.get("popularity");
        //System.out.println("Scheduler, createPlayedTrack: 5.");

        List<Artist> trackArtists = new ArrayList<>();
        List<Map<String, Object>> trackArtistMaps = (List<Map<String, Object>>) itemMap.get("artists");
        for (Map<String, Object> trackArtistMap : trackArtistMaps) {
            String trackArtistId = (String) trackArtistMap.get("id");
            String trackArtistName = (String) trackArtistMap.get("name");
            Artist trackArtist = new Artist(trackArtistId, trackArtistName);
            trackArtists.add(trackArtist);
        }
        //System.out.println("Scheduler, createPlayedTrack: 6.");

        Map<String, Object> albumMap = (Map<String, Object>) itemMap.get("album");
        String albumId = (String) albumMap.get("id");
        String albumType = (String) albumMap.get("album_type");
        String albumName = (String) albumMap.get("name");
        //System.out.println("Scheduler, createPlayedTrack: 7.");

        String albumReleaseDateRaw = (String) albumMap.get("release_date");
        String albumReleaseDatePrecision = (String) albumMap.get("release_date_precision");
        //System.out.println("Scheduler, createPlayedTrack: 8.");

        LocalDate albumReleaseDate = parseSpotifyDate(albumReleaseDateRaw, albumReleaseDatePrecision);
        //System.out.println("Scheduler, createPlayedTrack: 9.");


        List<Artist> albumArtists = new ArrayList<>();
        List<Map<String, Object>> albumArtistMaps = (List<Map<String, Object>>) albumMap.get("artists");
        for (Map<String, Object> albumArtistMap : albumArtistMaps) {
            String albumArtistId = (String) albumArtistMap.get("id");
            String albumArtistName = (String) albumArtistMap.get("name");
            Artist albumArtist = new Artist(albumArtistId, albumArtistName);
            albumArtists.add(albumArtist);
        }
        //System.out.println("Scheduler, createPlayedTrack: 10.");

        List<Map<String, Object>> imageMaps = (List<Map<String, Object>>) albumMap.get("images");
        String albumCover = (String) imageMaps.getFirst().get("url");
        //System.out.println("Scheduler, createPlayedTrack: 11.");

        Album album = new Album(albumId, albumName, albumCover, albumReleaseDate, albumReleaseDatePrecision, albumType, albumArtists);
        Track track = new Track(trackId, trackName, album, durationMs, isExplicit, isLocal, trackArtists);
        //System.out.println("Scheduler, createPlayedTrack: 12.");

        PlayedTrack playedTrack = new PlayedTrack(track,contextType, device, currentPopularity, timeFinished);
        System.out.println("Scheduler, createPlayedTrack: Ending.");
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
