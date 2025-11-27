
import java.time.LocalDate;
import java.util.List;
public record Album(String id, String name, String cover, LocalDate releaseDate, String releaseDatePrecision, String type, List<Artist> artists) {}
