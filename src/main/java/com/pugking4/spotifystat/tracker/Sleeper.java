package com.pugking4.spotifystat.tracker;

@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
