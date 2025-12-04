package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.*;

import java.sql.*;
import java.sql.Date;
import java.time.Instant;
import java.util.*;

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
        List<Artist> artists = new ArrayList<>(playedTrack.track().album().artists());
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
            stmt.setTimestamp(6, Timestamp.from(playedTrack.timeFinished()));

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
        INSERT INTO artists (id, name, followers, genres, image, popularity, updated_at)
        VALUES (?, ?, null, null, null, null, null)
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

    public static List<Artist> getArtists() {
        String sql = """
        SELECT id, name, followers, genres, image, popularity, updated_at
        FROM artists;
    """;
        List<Artist> artists = new ArrayList<>();

        Logger.println("Trying to get all artists...", 4);
        try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();
            Logger.println("Executed query...", 4);
            while (rs.next()) {
                String id =  rs.getString("id");
                Logger.println("Got id...", 5);
                Timestamp updateTimestamp = rs.getTimestamp("updated_at");
                Instant updatedAt = null;
                if (updateTimestamp != null) updatedAt = updateTimestamp.toInstant();
                Logger.println("Got updated_at...", 5);

                Artist artist = new Artist(id, null, null, null, null, null, updatedAt);
                artists.add(artist);
            }
            rs.close();
            Logger.println("Closed connection...", 4);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Logger.println("Finished.", 4);
        return artists;
    }

    public static void updateBatchArtists(List<Artist> artists) {
        String updateSql = """
        UPDATE artists SET name = ?, followers = ?, genres = ?, image = ?, popularity = ?, updated_at = ?
        WHERE id = ?
    """;

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(updateSql)) {
            for (Artist artist : artists) {
                ps.setString(1, artist.name());
                ps.setInt(2, artist.followers());
                ps.setString(3, String.join(",", artist.genres()));
                ps.setString(4, artist.image());
                ps.setInt(5, artist.popularity());
                ps.setTimestamp(6, Timestamp.from(artist.updatedAt()));
                ps.setString(7, artist.id());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
