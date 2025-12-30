package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;

import java.util.ArrayList;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        try {
            SpotifyWrapper.getCurrentlyPlayingTrack();
        } catch (Exception e) {
            Logger.println(e);
        }
        List<ScheduledTask> scheduledTasks = List.of(new TrackingPoller(), new ArtistUpdater());
        Scheduler scheduler = new Scheduler(scheduledTasks);
    }
}
