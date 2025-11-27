package com.pugking4.spotifytracker.tracker;

import java.sql.*;
import java.util.List;

public class DatabaseWrapper {
    private static final String URL = "jdbc:postgresql://localhost:5433/track-database";
    private static final String USER = "pugking4";
    private static final String PASSWORD = "apples";

    public static Connection getConnection() throws SQLException {
        Logger.println("DatabaseWrapper: Getting database connection.", 4);
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void insertPlayedTrack(PlayedTrack playedTrack) {
        Logger.println("DatabaseWrapper: Recording track play: " + playedTrack.track().name(), 3);
        List<Artist> artists = playedTrack.track().album().artists();
        artists.addAll(playedTrack.track().artists());
        for (Artist artist : artists) {
            Logger.println("DatabaseWrapper: Inserting artist: " + artist.name(), 4);
            insertArtist(artist);
        }

        Album album = playedTrack.track().album();
        insertAlbum(album);
        for (Artist artist : album.artists()) {
            insertAlbumArtist(artist, album);
        }

        Track track = playedTrack.track();
        insertTrack(track);
        for (Artist artist : track.artists()) {
            insertTrackArtist(artist, track);
        }

        insertDevice(playedTrack.device());
        int id = insertTrackHistory(playedTrack);
        Logger.println("Finished recording track play, id: " + id + ".", 3);
    }

    private static int insertTrackHistory(PlayedTrack playedTrack) {
        String sql = """
        INSERT INTO track_history (context_type, album_id, track_id, device_name, current_popularity, time_finished)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING id
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playedTrack.contextType());
            stmt.setString(2, playedTrack.track().album().id());
            stmt.setString(3, playedTrack.track().id());
            stmt.setString(4, playedTrack.device().name());
            stmt.setInt(5, playedTrack.currentPopularity());
            stmt.setTimestamp(6, Timestamp.from(playedTrack.time_played()));

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
            throw new RuntimeException("Insert failed: no ID returned");

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static void insertArtist(Artist artist) {
        String sql = """
        INSERT INTO artists (id, name)
        VALUES (?, ?)
        ON CONFLICT (id) DO NOTHING
    """;
        Logger.println("About to start connection.", 4);
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            Logger.println("Inside connection.", 4);
            stmt.setString(1, artist.id());
            stmt.setString(2, artist.name());

            stmt.executeUpdate();
            Logger.println("Executed update.", 4);

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
        Logger.println("Ended.", 4);
    }

    private static void insertTrack(Track track) {
        String sql = """
        INSERT INTO tracks (id, name, album_id, duration_ms, is_explicit, is_local)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, track.id());
            stmt.setString(2, track.name());
            stmt.setString(3, track.album().id());
            stmt.setInt(4, track.durationMs());
            stmt.setBoolean(5, track.isExplicit());
            stmt.setBoolean(6, track.isLocal());

            stmt.executeUpdate();

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static void insertAlbum(Album album) {
        String sql = """
        INSERT INTO albums (id, name, cover, release_date, release_date_precision, album_type)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, album.id());
            stmt.setString(2, album.name());
            stmt.setString(3, album.cover());
            stmt.setDate(4, Date.valueOf(album.releaseDate()));
            stmt.setString(5, album.releaseDatePrecision());
            stmt.setString(6, album.type());

            stmt.executeUpdate();

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static void insertDevice(Device device) {
        String sql = """
        INSERT INTO devices (name, type)
        VALUES (?, ?)
        ON CONFLICT (name) DO NOTHING
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, device.name());
            stmt.setString(2, device.type());

            stmt.executeUpdate();

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static void insertAlbumArtist(Artist artist, Album album) {
        String sql = """
        INSERT INTO album_artist (album_id, artist_id)
        VALUES (?, ?)
        ON CONFLICT (album_id, artist_id) DO NOTHING
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, album.id());
            stmt.setString(2, artist.id());

            stmt.executeUpdate();

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }

    private static void insertTrackArtist(Artist artist, Track track) {
        String sql = """
        INSERT INTO track_artist (track_id, artist_id)
        VALUES (?, ?)
        ON CONFLICT (track_id, artist_id) DO NOTHING
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, track.id());
            stmt.setString(2, artist.id());

            stmt.executeUpdate();

        } catch (SQLException e) {
            Logger.log("not sure", e);
            throw new RuntimeException(e);
        }
    }
}
