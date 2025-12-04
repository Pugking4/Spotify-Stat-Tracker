package com.pugking4.spotifystat.tracker;

import java.util.concurrent.ScheduledFuture;

public interface ScheduledTask {
    public int getRequiredNumberOfThreads();
    public Runnable getTask();
    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture);
    public ScheduledFuture<?> getScheduledFuture();
    public int getDelay();
    public int getInitialDelay();
    public DelayType getDelayType();
    public boolean isPendingDelayChange();
    public void setWrappedTask(Runnable wrappedTask);
    public Runnable getWrappedTask();
    public boolean isFirstRun();
    public void resolvePendingDelayChange();
}
