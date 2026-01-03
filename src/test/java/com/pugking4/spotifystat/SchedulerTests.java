package com.pugking4.spotifystat;

import com.pugking4.spotifystat.tracker.DelayType;
import com.pugking4.spotifystat.tracker.ScheduledTaskSpecification;
import com.pugking4.spotifystat.tracker.Scheduler;
import com.pugking4.spotifystat.tracker.SpotifyOAuthServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SchedulerTests {
    @Mock
    ScheduledExecutorService executor;
    @Mock
    ScheduledFuture<?> future;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void test_start_schedules_fixed_rate_specs() {
        var ran = new AtomicBoolean(false);
        ScheduledTaskSpecification spec = new ScheduledTaskSpecification(
                "poller",
                () -> ran.set(true),
                DelayType.FIXED_RATE,
                Duration.ZERO,
                () -> Duration.ofSeconds(5)
        );

        doReturn(future).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        Scheduler s = new Scheduler(Set.of(spec), executor);
        s.start();

        verify(executor).scheduleAtFixedRate(any(Runnable.class), eq(0L), eq(5L), eq(TimeUnit.SECONDS));
    }

    @Test
    void test_start_schedules_fixed_rate_specs_and_stops() {
        var ran = new AtomicBoolean(false);
        ScheduledTaskSpecification spec = new ScheduledTaskSpecification(
                "poller",
                () -> ran.set(true),
                DelayType.FIXED_RATE,
                Duration.ZERO,
                () -> Duration.ofSeconds(5)
        );

        doReturn(future).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        Scheduler s = new Scheduler(Set.of(spec), executor);
        s.start();
        s.stop();

        verify(future).cancel(true);
        verify(executor).shutdown();
    }

    @Test
    void test_start_schedules_fixed_delay_specs() {
        var ran = new AtomicBoolean(false);
        ScheduledTaskSpecification spec = new ScheduledTaskSpecification(
                "poller",
                () -> ran.set(true),
                DelayType.FIXED_DELAY,
                Duration.ZERO,
                () -> Duration.ofSeconds(5)
        );

        doReturn(future).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        Scheduler s = new Scheduler(Set.of(spec), executor);
        s.start();

        verify(executor).scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(5L), eq(TimeUnit.SECONDS));
    }

    @Test
    void test_wrap_executes_task_try_path() {
        var ran = new AtomicBoolean(false);
        ScheduledTaskSpecification spec = new ScheduledTaskSpecification(
                "poller",
                () -> ran.set(true),
                DelayType.FIXED_RATE,
                Duration.ZERO,
                () -> Duration.ofSeconds(5)
        );

        doReturn(future).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        Scheduler s = new Scheduler(Set.of(spec), executor);
        s.start();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleAtFixedRate(captor.capture(), eq(0L), eq(5L), eq(TimeUnit.SECONDS));

        captor.getValue().run();
        assertTrue(ran.get());
    }

    @Test
    void test_wrapped_task_throws_error() {
        var ran = new AtomicBoolean(false);
        AtomicInteger runs = new AtomicInteger(0);
        ScheduledTaskSpecification spec = new ScheduledTaskSpecification(
                "poller",
                () -> {
                    int n = runs.incrementAndGet();
                    if (n == 1) throw new RuntimeException("test");
                    ran.set(true);
                },
                DelayType.FIXED_RATE,
                Duration.ZERO,
                () -> Duration.ofSeconds(5)
        );

        doReturn(future).when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        Scheduler s = new Scheduler(Set.of(spec), executor);
        s.start();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleAtFixedRate(captor.capture(), eq(0L), eq(5L), eq(TimeUnit.SECONDS));

        Runnable wrapped = captor.getValue();

        assertDoesNotThrow(wrapped::run);
        assertFalse(ran.get());

        assertDoesNotThrow(wrapped::run);
        assertTrue(ran.get());
    }


}
