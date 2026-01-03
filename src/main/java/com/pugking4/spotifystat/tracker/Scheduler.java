package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Scheduler {
    private final ScheduledExecutorService executor;
    Set<ScheduledTaskSpecification> taskSpecifications;
    Map<ScheduledTaskSpecification, ScheduledFuture<?>> futures = new HashMap<>();

    @ExcludeFromJacocoGeneratedReport
    public Scheduler(Set<ScheduledTaskSpecification> specs) {
        this.taskSpecifications = specs;
        this.executor = Executors.newScheduledThreadPool(specs.size());
    }

    public Scheduler(Set<ScheduledTaskSpecification> specs, ScheduledExecutorService executor) {
        this.taskSpecifications = specs;
        this.executor = executor;
    }

    public void start() {
        for (ScheduledTaskSpecification spec : taskSpecifications) {
            schedule(spec);
        }
    }

    public void stop() {
        for (ScheduledFuture<?> f : futures.values()) {
            if (f != null) f.cancel(true);
        }
        executor.shutdown();
    }

    private void schedule(ScheduledTaskSpecification spec) {
        Runnable wrapped = wrap(spec);

        ScheduledFuture<?> f = switch (spec.delayType()) {
            case FIXED_RATE ->
                    executor.scheduleAtFixedRate(wrapped, spec.initialDelay().toSeconds(), spec.delay().get().toSeconds(), TimeUnit.SECONDS);
            case FIXED_DELAY ->
                    executor.scheduleWithFixedDelay(wrapped, spec.initialDelay().toSeconds(), spec.delay().get().toSeconds(), TimeUnit.SECONDS);
        };

        futures.put(spec, f);
    }


    private Runnable wrap(ScheduledTaskSpecification spec) {
        return () -> {
            try {
                spec.task().run();
            } catch (Exception e) {
                Logger.println(e);
                Logger.log("Task " + spec.description() + " threw an exception", e);
            }
        };
    }
}
