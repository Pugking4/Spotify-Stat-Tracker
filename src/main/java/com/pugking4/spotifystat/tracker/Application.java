package com.pugking4.spotifystat.tracker;

import java.util.ArrayList;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        List<ScheduledTask> scheduledTasks = List.of(new TrackingPoller(), new ArtistUpdater());
        Scheduler scheduler = new Scheduler(scheduledTasks);
    }
}
