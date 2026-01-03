package com.pugking4.spotifystat.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;

public class Application {
    public static void main(String[] args) {
        SpotifyWrapper spotifyWrapper = new SpotifyWrapper(HttpClient.newBuilder().build(), new ObjectMapper(), TokenManager.getInstance());
        Set<ScheduledTaskSpecification> specs = Set.of(new TrackingPoller(spotifyWrapper).spec(), new ArtistUpdater(spotifyWrapper).spec());
        Scheduler scheduler = new Scheduler(specs);

        scheduler.start();
    }
}
