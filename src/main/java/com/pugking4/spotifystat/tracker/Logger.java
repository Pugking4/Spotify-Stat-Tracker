package com.pugking4.spotifystat.tracker;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private final static int level = 5;
    private final static boolean timeOn = false;

    private static final ZoneId AEST_ZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z");
    private static final String LOG_FILE = "./log.txt";

    public static void println(String message) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerClassName = "Unknown";
        String callerMethodName = "Unknown";
        for (int i = 2; i < stackTrace.length; i++) {
            if (!stackTrace[i].getClassName().equals(Logger.class.getName())) {
                callerClassName = stackTrace[i].getClassName();
                callerMethodName = stackTrace[i].getMethodName();
                break;
            }
        }

        ZonedDateTime dateTime = Instant.now().atZone(AEST_ZONE);
        String formattedDateTime = dateTime.format(FORMATTER);

        System.out.print(callerClassName);
        System.out.print(", " + callerMethodName);
        if (timeOn) System.out.print(" at " + formattedDateTime);
        System.out.print(" | ");
        System.out.println(message);
    }

    public static void println(String message, int logLevel) {
        if (logLevel <= level) println(message);
    }

    public static void println(Throwable t) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerClassName = "Unknown";
        String callerMethodName = "Unknown";
        for (int i = 2; i < stackTrace.length; i++) {
            if (!stackTrace[i].getClassName().equals(Logger.class.getName())) {
                callerClassName = stackTrace[i].getClassName();
                callerMethodName = stackTrace[i].getMethodName();
                break;
            }
        }

        ZonedDateTime dateTime = Instant.now().atZone(AEST_ZONE);
        String formattedDateTime = dateTime.format(FORMATTER);

        System.out.print(callerClassName);
        System.out.print(", " + callerMethodName);
        if (timeOn) System.out.print(" at " + formattedDateTime);
        System.out.println(" | Exception:");

        System.out.println(t.toString());
        for (StackTraceElement elem : t.getStackTrace()) {
            System.out.println("\tat " + elem.toString());
        }
    }


    public static synchronized void log(String message, Throwable t) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String callerClassName = "Unknown";
        String callerMethodName = "Unknown";
        for (int i = 2; i < stackTrace.length; i++) {
            if (!stackTrace[i].getClassName().equals(Logger.class.getName())) {
                callerClassName = stackTrace[i].getClassName();
                callerMethodName = stackTrace[i].getMethodName();
                break;
            }
        }

        ZonedDateTime dateTime = Instant.now().atZone(AEST_ZONE);
        String formattedDateTime = dateTime.format(FORMATTER);

        StringBuilder logMsg = new StringBuilder();
        logMsg.append(formattedDateTime).append(" | ")
                .append(callerClassName).append(", ")
                .append(callerMethodName).append(" | ")
                .append(message);

        if (t != null) {
            logMsg.append("\nException: ").append(t.toString());
            for (StackTraceElement elem : t.getStackTrace()) {
                logMsg.append("\n\tat ").append(elem.toString());
            }
        }

        // Append log message to file
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println(logMsg.toString());
        } catch (IOException e) {
            System.err.println("Failed to write log to file: " + e.getMessage());
            System.err.println(logMsg.toString());
        }
    }

}
