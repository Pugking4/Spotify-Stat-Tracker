package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.updater.ScheduledUpdater;

public class Application {
    public static void main(String[] args) {
        ScheduledUpdater.start();
        Scheduler scheduler = new Scheduler();
    }
}
