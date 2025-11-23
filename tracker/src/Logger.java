import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private final static int level = 4;

    private static final ZoneId AEST_ZONE = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z");

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
        System.out.print(" at " + formattedDateTime + " | ");
        System.out.println(message);
    }

    public static void println(String message, int logLevel) {
        if (logLevel <= level) println(message);
    }
}
