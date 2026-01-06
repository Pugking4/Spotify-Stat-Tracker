package com.pugking4.spotifystat.tracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import java.util.Set;

public class Application {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().directory(".").load();

        SpotifyWrapper spotifyWrapper = new SpotifyWrapper(HttpClient.newBuilder().build(), new ObjectMapper(), TokenManager.getInstance());

        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[] {dotenv.get("DATABASE_HOST")});
        ds.setPortNumbers(new int[] {Integer.parseInt(dotenv.get("DATABASE_PORT"))});
        ds.setDatabaseName("track-database");
        ds.setUser(dotenv.get("DATABASE_USERNAME"));
        ds.setPassword(dotenv.get("DATABASE_PASSWORD"));
        DatabaseWrapper databaseWrapper = new DatabaseWrapper(ds);

        PriorityClassifier priorityClassifier = new PriorityClassifier(Clock.systemDefaultZone());
        Set<ScheduledTaskSpecification> specs = Set.of(new TrackingPoller(spotifyWrapper, databaseWrapper).spec(), new ArtistUpdater(spotifyWrapper, databaseWrapper, priorityClassifier).spec());
        Scheduler scheduler = new Scheduler(specs);

        scheduler.start();
    }
}
