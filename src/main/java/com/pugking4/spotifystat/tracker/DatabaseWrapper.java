package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.dto.*;
import com.pugking4.spotifystat.common.logging.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DatabaseWrapper {
    private final DataSource ds;

    public static final String INSERT_TRACK_HISTORY_SQL = """
        INSERT INTO track_history (context_type, album_id, track_id, device_name, current_popularity, time_finished)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING id
    """;
    public static final String INSERT_ARTIST_SQL = """
        INSERT INTO artists (id, name, followers, genres, image, popularity, updated_at)
        VALUES (?, ?, null, null, null, null, null)
        ON CONFLICT (id) DO NOTHING
    """;
    public static final String INSERT_TRACK_SQL = """
        INSERT INTO tracks (id, name, album_id, duration_ms, is_explicit, is_local)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
    """;
    public static final String INSERT_ALBUM_SQL = """
        INSERT INTO albums (id, name, cover, release_date, release_date_precision, album_type)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
    """;
    public static final String INSERT_DEVICE_SQL = """
        INSERT INTO devices (name, type)
        VALUES (?, ?)
        ON CONFLICT (name) DO NOTHING
    """;
    public static final String INSERT_ALBUM_ARTIST_SQL = """
        INSERT INTO album_artist (album_id, artist_id)
        VALUES (?, ?)
        ON CONFLICT (album_id, artist_id) DO NOTHING
    """;
    public static final String INSERT_TRACK_ARTIST_SQL = """
        INSERT INTO track_artist (track_id, artist_id)
        VALUES (?, ?)
        ON CONFLICT (track_id, artist_id) DO NOTHING
    """;
    public static final String GET_ALL_ARTISTS_SQL = """
        SELECT id, name, followers, genres, image, popularity, updated_at
        FROM artists;
    """;
    public static final String UPDATE_BATCH_ARTISTS_SQL = """
        UPDATE artists SET name = ?, followers = ?, genres = ?, image = ?, popularity = ?, updated_at = ?
        WHERE id = ?
    """;

    public DatabaseWrapper(DataSource ds) {
        this.ds = ds;
    }

    private Connection getConnection() throws SQLException {
        Logger.println("DatabaseWrapper: Getting database connection.", 4);
        return ds.getConnection();
    }

    public void insertPlayedTrack(PlayedTrack playedTrack) {
        Logger.println("DatabaseWrapper: Recording track play: " + playedTrack.track().name(), 3);
        List<Artist> dupedArtists = new ArrayList<>(playedTrack.track().album().artists());
        dupedArtists.addAll(playedTrack.track().artists());
        Set<Artist> artists = new HashSet<>(dupedArtists.stream()
                .collect(Collectors.toMap(Artist::id, a -> a, (existing, replacement) -> existing))
                .values());

        try (Connection conn = getConnection()) {
            boolean old = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (Artist artist : artists) {
                    Logger.println("DatabaseWrapper: Inserting artist: " + artist.name(), 4);
                    insertArtist(artist, conn);
                }

                Album album = playedTrack.track().album();
                insertAlbum(album, conn);
                for (Artist artist : album.artists()) {
                    insertAlbumArtist(artist, album, conn);
                }

                Track track = playedTrack.track();
                insertTrack(track, conn);
                for (Artist artist : track.artists()) {
                    insertTrackArtist(artist, track, conn);
                }

                insertDevice(playedTrack.device(), conn);
                int id = insertTrackHistory(playedTrack, conn);
                Logger.println("Finished recording track play, id: " + id + ".", 3);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(old);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int insertTrackHistory(PlayedTrack playedTrack, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRACK_HISTORY_SQL)) {
            stmt.setString(1, playedTrack.contextType());
            stmt.setString(2, playedTrack.track().album().id());
            stmt.setString(3, playedTrack.track().id());
            stmt.setString(4, playedTrack.device().name());
            stmt.setInt(5, playedTrack.currentPopularity());
            stmt.setTimestamp(6, Timestamp.from(playedTrack.timeFinished()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Insert failed: no ID returned");
    }

    private void insertArtist(Artist artist, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_ARTIST_SQL)) {
            Logger.println("Inside connection.", 4);
            stmt.setString(1, artist.id());
            stmt.setString(2, artist.name());

            stmt.executeUpdate();
            Logger.println("Executed update.", 4);
        }
    }

    private void insertTrack(Track track, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRACK_SQL)) {
            stmt.setString(1, track.id());
            stmt.setString(2, track.name());
            stmt.setString(3, track.album().id());
            stmt.setInt(4, track.durationMs());
            stmt.setBoolean(5, track.isExplicit());
            stmt.setBoolean(6, track.isLocal());

            stmt.executeUpdate();
        }
    }

    private void insertAlbum(Album album, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_ALBUM_SQL)) {
            stmt.setString(1, album.id());
            stmt.setString(2, album.name());
            stmt.setString(3, album.cover());
            stmt.setDate(4, Date.valueOf(album.releaseDate()));
            stmt.setString(5, album.releaseDatePrecision());
            stmt.setString(6, album.type());

            stmt.executeUpdate();
        }
    }

    private void insertDevice(Device device, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_DEVICE_SQL)) {
            stmt.setString(1, device.name());
            stmt.setString(2, device.type());

            stmt.executeUpdate();
        }
    }

    private void insertAlbumArtist(Artist artist, Album album, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_ALBUM_ARTIST_SQL)) {
            stmt.setString(1, album.id());
            stmt.setString(2, artist.id());

            stmt.executeUpdate();
        }
    }

    private void insertTrackArtist(Artist artist, Track track, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(INSERT_TRACK_ARTIST_SQL)) {
            stmt.setString(1, track.id());
            stmt.setString(2, artist.id());

            stmt.executeUpdate();
        }
    }

    public List<Artist> getAllSkeletonArtists() {
        List<Artist> artists = new ArrayList<>();

        Logger.println("Trying to get all artists...", 4);
        try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(GET_ALL_ARTISTS_SQL); ResultSet rs = ps.executeQuery()) {
            Logger.println("Executed query...", 4);
            while (rs.next()) {
                String id = rs.getString("id");
                Logger.println("Got id...", 5);
                Timestamp updateTimestamp = rs.getTimestamp("updated_at");
                Instant updatedAt = null;
                if (updateTimestamp != null) updatedAt = updateTimestamp.toInstant();
                Logger.println("Got updated_at...", 5);
                Artist artist = new Artist(id, null, null, null, null, null, updatedAt);
                artists.add(artist);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Logger.println("Finished.", 4);
        return artists;
    }

    public void updateBatchArtists(List<Artist> artists) {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(UPDATE_BATCH_ARTISTS_SQL)) {
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
