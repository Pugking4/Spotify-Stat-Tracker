package com.pugking4.spotifystat.tracker;

import java.time.Duration;
import java.util.function.Supplier;

public record ScheduledTaskSpecification(String description, Runnable task, DelayType delayType, Duration initialDelay, Supplier<Duration> delay) {

}
