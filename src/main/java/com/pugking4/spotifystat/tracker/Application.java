package com.pugking4.spotifystat.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pugking4.spotifystat.common.logging.Logger;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        SpotifyWrapper spotifyWrapper = new SpotifyWrapper(HttpClient.newBuilder().build(), new ObjectMapper(), TokenManager.getInstance());
        List<ScheduledTask> scheduledTasks = List.of(new TrackingPoller(spotifyWrapper), new ArtistUpdater(spotifyWrapper));
        Scheduler scheduler = new Scheduler(scheduledTasks);
    }
}
