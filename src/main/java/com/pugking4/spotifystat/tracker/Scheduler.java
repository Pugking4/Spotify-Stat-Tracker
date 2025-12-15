package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {
    private final ScheduledExecutorService executor;
    private final List<ScheduledTask> scheduledTasks;

    public Scheduler(List<ScheduledTask> scheduledTasks) {
        this.scheduledTasks = scheduledTasks;
        int coreCount = scheduledTasks.stream()
                .mapToInt(ScheduledTask::getRequiredNumberOfThreads)
                .sum();
        this.executor = Executors.newScheduledThreadPool(coreCount);
        for (ScheduledTask scheduledTask : scheduledTasks) {
            scheduledTask.setWrappedTask(wrapTask(scheduledTask));
            scheduleTask(scheduledTask);
        }
    }

    private void onStart(ScheduledTask scheduledTask) {
        if (scheduledTask.isPendingDelayChange()) {
            changeReadInterval(scheduledTask);
            scheduledTask.resolvePendingDelayChange();
        }
    }

    private void changeReadInterval(ScheduledTask scheduledTask)
    {
        if (scheduledTask.getDelay() > 0)
        {
            if (scheduledTask.getScheduledFuture() != null)
            {
                scheduledTask.getScheduledFuture().cancel(true);
            }
            scheduleTask(scheduledTask);
        }
    }

    private void scheduleTask(ScheduledTask scheduledTask) {
        int initialDelay = 0;
        if (scheduledTask.isFirstRun()) {
            initialDelay = scheduledTask.getInitialDelay();
        }

        switch (scheduledTask.getDelayType()) {
            case FIXED_DELAY -> scheduledTask.setScheduledFuture(executor.scheduleWithFixedDelay(scheduledTask.getWrappedTask(), initialDelay, scheduledTask.getDelay(), TimeUnit.SECONDS));
            case FIXED_RATE -> scheduledTask.setScheduledFuture(executor.scheduleAtFixedRate(scheduledTask.getWrappedTask(), initialDelay, scheduledTask.getDelay(), TimeUnit.SECONDS));
            case DYNAMIC_DELAY -> scheduleTaskWithDynamicDelay(scheduledTask);
        }
    }

    private void scheduleTaskWithDynamicDelay(ScheduledTask scheduledTask) {
        Runnable task = () -> {
            try {
                long start = System.currentTimeMillis();
                // Run your actual task logic here
                scheduledTask.getWrappedTask().run();
                long end = System.currentTimeMillis();
                long taskDurationSeconds = (end - start) / 1000;

                // Calculate the next delay
                int nextDelay = Math.max(0, scheduledTask.getDelay() - (int) taskDurationSeconds);

                // Schedule the next execution
                reschedule(nextDelay, scheduledTask);
            } catch (Exception e) {
                Logger.log("Task threw an exception", e);
                // Optionally reschedule with a default delay on error
                reschedule(scheduledTask.getDelay(), scheduledTask);
            }
        };
        scheduledTask.setScheduledFuture(executor.schedule(task, 0, TimeUnit.SECONDS));
    }


    private Runnable wrapTask(ScheduledTask task) {
        return () -> {
            try {
                onStart(task);
                task.getTask().run();
            } catch (Exception e) {
                Logger.println(e);
                Logger.log("Task " + task.getClass().getSimpleName() + " threw an exception", e);
            }
        };
    }

    public void reschedule(int delay, ScheduledTask scheduledTask) {
        if (scheduledTask.getScheduledFuture() != null) {
            scheduledTask.getScheduledFuture().cancel(true);
        }
        scheduledTask.setScheduledFuture(executor.schedule(scheduledTask.getWrappedTask(), delay, TimeUnit.SECONDS));
    }


}
