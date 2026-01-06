package com.pugking4.spotifystat;

import com.pugking4.spotifystat.common.dto.Artist;
import com.pugking4.spotifystat.tracker.*;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ArtistUpdaterTests {
    @Mock
    private SpotifyWrapper spotifyWrapper;
    @Mock
    private DatabaseWrapper databaseWrapper;

    private PriorityClassifier priorityClassifier;
    private Clock clock;

    private ArtistUpdater artistUpdater;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        priorityClassifier = new PriorityClassifier(clock);

        artistUpdater = new ArtistUpdater(spotifyWrapper, databaseWrapper, priorityClassifier);
    }

    @Test
    void test_returns_correct_initial_state_spec() {
        ScheduledTaskSpecification spec = artistUpdater.spec();

        assertEquals("Artist Updater", spec.description());
        assertEquals(DelayType.FIXED_DELAY, spec.delayType());
        assertEquals(Duration.ZERO, spec.initialDelay());
        assertEquals(Duration.ofSeconds(30), spec.delay().get());
    }

    Artist createSkeletonArtist(String id, Instant updatedAt) {
        return new Artist(
                id,
                null,
                null,
                null,
                null,
                null,
                updatedAt
        );
    }

    @Test
    void test_correctly_sorts_and_filters_artists() {
        List<Artist> artists = new ArrayList<>();
        artists.add(createSkeletonArtist("0", clock.instant().minusSeconds(60 * 87)));
        artists.add(createSkeletonArtist("1", clock.instant().minusSeconds(60 * 60 * 77)));
        artists.add(createSkeletonArtist("2", clock.instant().minusSeconds(60 * 60 * 3)));
        artists.add(createSkeletonArtist("3", clock.instant().minusSeconds(60 * 60 * 23)));
        artists.add(createSkeletonArtist("4", clock.instant().minusSeconds(60 * 60 * 40)));
        artists.add(createSkeletonArtist("5", clock.instant().minusSeconds(60 * 2)));
        artists.add(createSkeletonArtist("6", clock.instant().minusSeconds(60 * 60 * 10)));

        when(databaseWrapper.getAllSkeletonArtists()).thenReturn(artists);
        when(spotifyWrapper.getBatchArtists(any())).thenReturn(List.of());

        artistUpdater.spec().task().run();

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(spotifyWrapper).getBatchArtists(captor.capture());
        var list = captor.getValue();

        assertAll("Correct ordering and filtering", () -> {
            assertEquals("1", list.get(0));
            assertEquals("4", list.get(1));
            assertEquals("3", list.get(2));
        });

        assertEquals(3, list.size());
    }

    @Test
    void test_empty_getAllSkeletonArtists() {
        when(databaseWrapper.getAllSkeletonArtists()).thenReturn(List.of());
        when(spotifyWrapper.getBatchArtists(any())).thenReturn(List.of());

        artistUpdater.spec().task().run();

        verify(spotifyWrapper, never()).getBatchArtists(any());
        verify(databaseWrapper, never()).updateBatchArtists(any());
    }

    @Test
    void test_all_DO_NOT_UPDATE() {
        List<Artist> skeletonArtists = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            skeletonArtists.add(createSkeletonArtist(Integer.toString(i), clock.instant().minusSeconds((i * 60 * 20) % (60 * 60 * 12))));
        }

        when(databaseWrapper.getAllSkeletonArtists()).thenReturn(skeletonArtists);
        when(spotifyWrapper.getBatchArtists(any())).thenReturn(List.of());

        artistUpdater.spec().task().run();

        verify(spotifyWrapper, never()).getBatchArtists(any());
        verify(databaseWrapper, never()).updateBatchArtists(any());
    }

    @Test
    void test_correctly_stores_updated_artists() {
        List<Artist> artists = TestUtilities.getSkeletonArtists(27);
        var updatedArtists = TestUtilities.getArtistsRaw(27);

        when(databaseWrapper.getAllSkeletonArtists()).thenReturn(artists);
        when(spotifyWrapper.getBatchArtists(any())).thenReturn(updatedArtists);

        artistUpdater.spec().task().run();

        verify(databaseWrapper).getAllSkeletonArtists();
        verify(spotifyWrapper).getBatchArtists(any());
        ArgumentCaptor<List<Artist>> captor = ArgumentCaptor.forClass(List.class);
        verify(databaseWrapper).updateBatchArtists(captor.capture());
        var list = captor.getValue();

        assertEquals(27, list.size());
    }
}
