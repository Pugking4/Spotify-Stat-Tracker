package com.pugking4.spotifystat.tracker;

import java.time.Clock;
import java.time.Instant;

public class PriorityClassifier {
    private final Clock clock;

    private static final int HIGH_DELAY = 72 * 60 * 60;
    private static final int MID_DELAY = 24 * 60 * 60;
    private static final int LOW_DELAY = 12 * 60 * 60;

    public PriorityClassifier(Clock clock) {
        this.clock = clock;
    }

    public Priority classify(Instant updatedAt) {
        Instant now = clock.instant();
        if (updatedAt == null) return Priority.MAX;
        else if (updatedAt.isBefore(now.minusSeconds(HIGH_DELAY))) return Priority.HIGH;
        else if (updatedAt.isBefore(now.minusSeconds(MID_DELAY))) return Priority.MEDIUM;
        else if (updatedAt.isBefore(now.minusSeconds(LOW_DELAY))) return Priority.LOW;
        else return Priority.DO_NOT_UPDATE;
    }

}
