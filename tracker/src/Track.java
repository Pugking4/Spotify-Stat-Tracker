import java.util.List;

public record Track(String id, String name, Album album, Integer durationMs, Boolean isExplicit, Boolean isLocal, List<Artist> artists) {}

