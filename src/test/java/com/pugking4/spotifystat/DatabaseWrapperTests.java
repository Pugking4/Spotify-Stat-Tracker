package com.pugking4.spotifystat;

import com.pugking4.spotifystat.common.dto.*;
import com.pugking4.spotifystat.tracker.DatabaseWrapper;
import com.pugking4.spotifystat.tracker.SpotifyApiException;
import com.pugking4.spotifystat.tracker.SpotifyWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DatabaseWrapperTests {
    @Mock
    DataSource ds;
    @Mock
    Connection conn;

    @Mock
    PreparedStatement insertArtist;
    @Mock
    PreparedStatement insertAlbum;
    @Mock
    PreparedStatement insertTrack;
    @Mock
    PreparedStatement insertDevice;
    @Mock
    PreparedStatement insertAlbumArtist;
    @Mock
    PreparedStatement insertTrackArtist;
    @Mock
    PreparedStatement insertTrackHistory;
    @Mock
    PreparedStatement generic;

    @Mock
    ResultSet rs;

    private DatabaseWrapper databaseWrapper;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        when(ds.getConnection()).thenReturn(conn);
    }

    private List<Artist> getArtists(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(i -> {
                        Instant base = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60L);
                        return new Artist(
                                "artist-" + i,                 // id
                                "Artist " + i,                 // name
                                1000 + (i * 25),               // followers
                                List.of("genre" + (i % 6)),    // genres
                                "https://example.com/img/" + i + ".jpg", // image
                                i % 100,                       // popularity
                                base      // updatedAt
                        );
                })
                .toList();
    }

    private List<Artist> getSkeletonArtists(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(i -> {
                    Instant base = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60L);
                    if (i % 2 == 0) base = null;
                    return new Artist(
                            "artist-" + i,                 // id
                            null,                 // name
                            null,               // followers
                            null,    // genres
                            null, // image
                            null,                       // popularity
                            base      // updatedAt
                    );
                })
                .toList();
    }

    private PlayedTrack getPlayedTrack() {
        return new PlayedTrack(
                new Track(
                        "0ws53An61dr49WfNuxWNKM",
                        "UMASUGIRU",
                        new Album(
                                "24LeTf9xct196GYmJ7Qemo",
                                "UMASUGIRU",
                                "https://i.scdn.co/image/ab67616d0000b2733bc37da83f50aa7309cfe094",
                                LocalDate.of(2026, 1, 1),
                                "day",
                                "single",
                                List.of(
                                        new Artist(
                                                "4f2l5pSKd1oUMEMx7SZBng",
                                                "pinponpanpon",
                                                8795,
                                                List.of(
                                                        "hyperpop"
                                                ),
                                                "https://i.scdn.co/image/ab6761610000e5eb22281a4924e0b1f558026609",
                                                31,
                                                Instant.now().minusSeconds(14400)
                                        )
                                )
                        ),
                        155384,
                        false,
                        false,
                        List.of(
                                new Artist(
                                        "4f2l5pSKd1oUMEMx7SZBng",
                                        "pinponpanpon",
                                        8795,
                                        List.of(
                                                "hyperpop"
                                        ),
                                        "https://i.scdn.co/image/ab6761610000e5eb22281a4924e0b1f558026609",
                                        31,
                                        Instant.now().minusSeconds(14400)
                                )
                        )
                ),
                "collection",
                new Device(
                        "JOSHU-PC",
                        "Computer"
                ),
                0,
                Instant.now().minusSeconds(180)
        );
    }

    @Test
    void test_insertPlayedTrack_commits_on_success() throws Exception {
        when(conn.getAutoCommit()).thenReturn(true);

        when(conn.prepareStatement(DatabaseWrapper.INSERT_ARTIST_SQL)).thenReturn(insertArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_SQL)).thenReturn(insertAlbum);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_SQL)).thenReturn(insertTrack);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_DEVICE_SQL)).thenReturn(insertDevice);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_ARTIST_SQL)).thenReturn(insertAlbumArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_ARTIST_SQL)).thenReturn(insertTrackArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_HISTORY_SQL)).thenReturn(insertTrackHistory);

        when(insertTrackHistory.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("id")).thenReturn(123);

        PlayedTrack played = getPlayedTrack();

        new DatabaseWrapper(ds).insertPlayedTrack(played);

        verify(conn).setAutoCommit(false);
        verify(conn).commit();
        verify(conn, never()).rollback();
        verify(conn).setAutoCommit(true);
    }

    @Test
    void test_insertPlayedTrack_no_id_throws_sqlexception() throws Exception {
        when(conn.getAutoCommit()).thenReturn(true);

        when(conn.prepareStatement(DatabaseWrapper.INSERT_ARTIST_SQL)).thenReturn(insertArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_SQL)).thenReturn(insertAlbum);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_SQL)).thenReturn(insertTrack);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_DEVICE_SQL)).thenReturn(insertDevice);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_ARTIST_SQL)).thenReturn(insertAlbumArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_ARTIST_SQL)).thenReturn(insertTrackArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_HISTORY_SQL)).thenReturn(insertTrackHistory);

        when(insertTrackHistory.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        PlayedTrack played = getPlayedTrack();


        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            new DatabaseWrapper(ds).insertPlayedTrack(played);
        });

        assertSame(SQLException.class, ex.getCause().getClass());
        assertTrue(ex.getMessage().contains("Insert failed: no ID returned"));
    }

    @Test
    void test_insertPlayedTrack_rollback_on_exception() throws Exception {
        when(conn.getAutoCommit()).thenReturn(true);

        when(conn.prepareStatement(DatabaseWrapper.INSERT_ARTIST_SQL)).thenReturn(insertArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_SQL)).thenReturn(insertAlbum);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_SQL)).thenReturn(insertTrack);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_DEVICE_SQL)).thenThrow(new SQLException("test"));
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_ARTIST_SQL)).thenReturn(insertAlbumArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_ARTIST_SQL)).thenReturn(insertTrackArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_HISTORY_SQL)).thenReturn(insertTrackHistory);

        when(insertTrackHistory.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("id")).thenReturn(123);

        PlayedTrack played = getPlayedTrack();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            new DatabaseWrapper(ds).insertPlayedTrack(played);
        });

        verify(conn).setAutoCommit(false);
        verify(conn, never()).commit();
        verify(conn).rollback();
        verify(conn).setAutoCommit(true);

        assertSame(SQLException.class, ex.getCause().getClass());
        assertTrue(ex.getMessage().contains("test"));
    }

    @Test
    void test_insertPlayedTrack_insert_functions_run_correct_amount_of_times() throws Exception {
        when(conn.getAutoCommit()).thenReturn(true);

        when(conn.prepareStatement(DatabaseWrapper.INSERT_ARTIST_SQL)).thenReturn(insertArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_SQL)).thenReturn(insertAlbum);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_SQL)).thenReturn(insertTrack);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_DEVICE_SQL)).thenReturn(insertDevice);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_ALBUM_ARTIST_SQL)).thenReturn(insertAlbumArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_ARTIST_SQL)).thenReturn(insertTrackArtist);
        when(conn.prepareStatement(DatabaseWrapper.INSERT_TRACK_HISTORY_SQL)).thenReturn(insertTrackHistory);

        when(insertTrackHistory.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("id")).thenReturn(123);

        PlayedTrack played = getPlayedTrack();

        new DatabaseWrapper(ds).insertPlayedTrack(played);

        verify(insertArtist, times(1)).executeUpdate();
        verify(insertAlbum, times(1)).executeUpdate();
        verify(insertTrack, times(1)).executeUpdate();
        verify(insertDevice, times(1)).executeUpdate();
        verify(insertAlbumArtist, times(1)).executeUpdate();
        verify(insertTrackArtist, times(1)).executeUpdate();
        verify(insertTrackHistory, times(1)).executeQuery();
    }

    @Test
    void test_getAllSkeletonArtists_standard() throws Exception {
        List<Artist> artists = getSkeletonArtists(4);

        when(conn.prepareStatement(DatabaseWrapper.GET_ALL_ARTISTS_SQL)).thenReturn(generic);

        when(generic.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, true, true, false);
        when(rs.getString("id")).thenReturn(artists.get(0).id(), artists.get(1).id(), artists.get(2).id(), artists.get(3).id());
        when(rs.getTimestamp("updated_at")).thenReturn(null, Timestamp.from(artists.get(1).updatedAt()), null, Timestamp.from(artists.get(3).updatedAt()));

        List<Artist> results = new DatabaseWrapper(ds).getAllSkeletonArtists();

        assertEquals(4, results.size());

        for (int i = 0; i < 4; i++) {
            assertEquals(artists.get(i), results.get(i));
        }
    }

    @Test
    void test_getAllSkeletonArtists_empty() throws Exception {
        when(conn.prepareStatement(DatabaseWrapper.GET_ALL_ARTISTS_SQL)).thenReturn(generic);

        when(generic.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        List<Artist> results = new DatabaseWrapper(ds).getAllSkeletonArtists();

        verify(generic, times(1)).executeQuery();

        assertEquals(0, results.size());
    }

    @Test
    void test_getAllSkeletonArtists_throw_sqlexception() throws Exception {
        when(conn.prepareStatement(DatabaseWrapper.GET_ALL_ARTISTS_SQL)).thenReturn(generic);

        when(generic.executeQuery()).thenThrow(new SQLException("rs-exception-test"));;

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            new DatabaseWrapper(ds).getAllSkeletonArtists();
        });

        assertSame(SQLException.class, ex.getCause().getClass());
        assertTrue(ex.getMessage().contains("rs-exception-test"));
    }

    @Test
    void test_updateBatchArtists_standard() throws Exception {
        // Arrange: build a small list with known values
        List<Artist> input = getArtists(27);
        when(conn.prepareStatement(DatabaseWrapper.UPDATE_BATCH_ARTISTS_SQL)).thenReturn(generic);

        new DatabaseWrapper(ds).updateBatchArtists(input);

        verify(generic, times(input.size())).addBatch();
        verify(generic, times(1)).executeBatch();

        // Assert: capture per-iteration values (example: ids)
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(generic, times(input.size())).setString(eq(7), idCaptor.capture());
        List<String> ids = idCaptor.getAllValues();

        for (int i = 0; i < input.size(); i++) {
            assertEquals(input.get(i).id(), ids.get(i));
        }
    }

    @Test
    void test_updateBatchArtists_max() throws Exception {
        // Arrange: build a small list with known values
        List<Artist> input = getArtists(50);
        when(conn.prepareStatement(DatabaseWrapper.UPDATE_BATCH_ARTISTS_SQL)).thenReturn(generic);

        new DatabaseWrapper(ds).updateBatchArtists(input);

        verify(generic, times(input.size())).addBatch();
        verify(generic, times(1)).executeBatch();

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(generic, times(input.size())).setString(eq(7), idCaptor.capture());
        List<String> ids = idCaptor.getAllValues();

        for (int i = 0; i < input.size(); i++) {
            assertEquals(input.get(i).id(), ids.get(i));
        }
    }

    @Test
    void test_updateBatchArtists_throw_sqlexception() throws Exception {
        // Arrange: build a small list with known values
        List<Artist> input = getArtists(50);
        when(conn.prepareStatement(DatabaseWrapper.UPDATE_BATCH_ARTISTS_SQL)).thenThrow(new SQLException("prepare-statement-exception-test"));;

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            new DatabaseWrapper(ds).updateBatchArtists(input);
        });

        assertSame(SQLException.class, ex.getCause().getClass());
        assertTrue(ex.getMessage().contains("prepare-statement-exception-test"));
    }
}