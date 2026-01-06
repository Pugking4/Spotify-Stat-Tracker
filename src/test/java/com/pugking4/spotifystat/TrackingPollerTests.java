package com.pugking4.spotifystat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pugking4.spotifystat.common.dto.PlayedTrack;
import com.pugking4.spotifystat.tracker.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class TrackingPollerTests {
    @Mock
    private SpotifyWrapper spotifyWrapper;
    @Mock
    private DatabaseWrapper databaseWrapper;

    private TrackingPoller trackingPoller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        trackingPoller = new TrackingPoller(spotifyWrapper, databaseWrapper);
    }

    @Test
    void test_returns_correct_initial_state_spec() {
        ScheduledTaskSpecification spec = trackingPoller.spec();

        assertEquals("Tracking Poller", spec.description());
        assertEquals(DelayType.FIXED_RATE, spec.delayType());
        assertEquals(Duration.ZERO, spec.initialDelay());
        assertEquals(Duration.ofSeconds(15), spec.delay().get());
    }

    @Test
    void test_run_does_nothing_when_poll_empty() {
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(Collections.emptyMap());

        ScheduledTaskSpecification spec = trackingPoller.spec();

        spec.task().run();

        verify(spotifyWrapper).getCurrentlyPlayingTrack();
        verifyNoInteractions(databaseWrapper);
        assertEquals(Duration.ofSeconds(15), spec.delay().get());
    }

    @Test
    void test_run_sets_active_mode_when_playing() throws JsonProcessingException {
        var map = TestUtilities.getPlayingTrackFull(0.05);
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(map);

        ScheduledTaskSpecification spec = trackingPoller.spec();

        spec.task().run();

        assertEquals(Duration.ofSeconds(5), spec.delay().get());
        verify(spotifyWrapper).getCurrentlyPlayingTrack();
        verifyNoInteractions(databaseWrapper);
    }

    @Test
    void test_run_sets_idle_mode_when_not_playing() throws JsonProcessingException {
        var map = TestUtilities.getPlayingTrackFull(0.25);
        map.put("is_playing", false);
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(map);

        ScheduledTaskSpecification spec = trackingPoller.spec();

        spec.task().run();

        assertEquals(Duration.ofSeconds(15), spec.delay().get());
        verifyNoInteractions(databaseWrapper);
    }

    @Test
    void test_run_sets_idle_mode_from_active() throws JsonProcessingException {
        var map1 = TestUtilities.getPlayingTrackFull(0.25);
        var map2 = TestUtilities.getPlayingTrackFull(0.30);
        map2.put("is_playing", false);
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(map1, map2);

        ScheduledTaskSpecification spec = trackingPoller.spec();

        spec.task().run();
        spec.task().run();

        assertEquals(Duration.ofSeconds(15), spec.delay().get());
        verifyNoInteractions(databaseWrapper);
    }

    @Test
    void test_doesnt_save_unfinished_track() throws JsonProcessingException {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (double p = 0.05; p < 0.65; p += 0.05) {
            maps.add(TestUtilities.getPlayingTrackFull(p));
        }
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(maps.get(0), maps.subList(1, maps.size()).toArray(new Map[0]));

        ScheduledTaskSpecification spec = trackingPoller.spec();

        for (int i = 0; i < maps.size(); i++) {
            spec.task().run();
        }

        verifyNoInteractions(databaseWrapper);
    }

    @Test
    void test_saves_finished_track_from_scratch() throws JsonProcessingException {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (double p = 0.05; p < 1.00; p += 0.05) {
            maps.add(TestUtilities.getPlayingTrackFull(p));
        }
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(maps.get(0), maps.subList(1, maps.size()).toArray(new Map[0]));
        var dMap = TestUtilities.getDevicesFull();
        when(spotifyWrapper.getAvailableDevices()).thenReturn(dMap);

        ScheduledTaskSpecification spec = trackingPoller.spec();

        for (int i = 0; i < maps.size(); i++) {
            spec.task().run();
        }

        verify(spotifyWrapper).getAvailableDevices();
        verify(databaseWrapper).insertPlayedTrack(any());
    }

    @Test
    void test_saves_finished_track_from_scratch_at_bounds() throws JsonProcessingException {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (double p = 0.00; p < 1.05; p += 0.05) {
            maps.add(TestUtilities.getPlayingTrackFull(p));
        }
        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(maps.get(0), maps.subList(1, maps.size()).toArray(new Map[0]));
        var dMap = TestUtilities.getDevicesFull();
        when(spotifyWrapper.getAvailableDevices()).thenReturn(dMap);

        ScheduledTaskSpecification spec = trackingPoller.spec();

        for (int i = 0; i < maps.size(); i++) {
            spec.task().run();
        }

        verify(spotifyWrapper).getAvailableDevices();
        verify(databaseWrapper).insertPlayedTrack(any());
    }

    @Test
    void test_resume_track_from_pause() throws JsonProcessingException {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (double p = 0.05; p < 0.50; p += 0.05) {
            maps.add(TestUtilities.getPlayingTrackFull(p));
        }
        for (double p = 0.50; p < 0.65; p += 0.05) {
            var map = TestUtilities.getPlayingTrackFull(p);
            map.put("is_playing", false);
            maps.add(map);
        }
        for (int i = 0; i < 100; i++) {
            maps.add(Collections.emptyMap());
        }
        for (double p = 0.65; p < 1.00; p += 0.05) {
            maps.add(TestUtilities.getPlayingTrackFull(p));
        }

        when(spotifyWrapper.getCurrentlyPlayingTrack()).thenReturn(maps.get(0), maps.subList(1, maps.size()).toArray(new Map[0]));
        var dMap = TestUtilities.getDevicesFull();
        when(spotifyWrapper.getAvailableDevices()).thenReturn(dMap);

        ScheduledTaskSpecification spec = trackingPoller.spec();

        for (int i = 0; i < maps.size(); i++) {
            spec.task().run();
        }

        verify(spotifyWrapper).getAvailableDevices();
        verify(databaseWrapper).insertPlayedTrack(any());
    }
}
