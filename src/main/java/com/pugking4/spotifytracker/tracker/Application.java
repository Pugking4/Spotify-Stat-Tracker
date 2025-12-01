package com.pugking4.spotifytracker.tracker;

import com.pugking4.spotifytracker.updater.ScheduledUpdater;

public class Application {
    public static void main(String[] args) {
        ScheduledUpdater.start();
        Scheduler scheduler = new Scheduler();
    }
}
