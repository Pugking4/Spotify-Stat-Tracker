package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CacheUtilities {
    private static final Path XDG_CACHE_DIR = getXDGCacheDir();

    public static byte[] read(String filename) throws IOException {
        Logger.println("Reading file: " + filename, 3);
        Path filePath = XDG_CACHE_DIR.resolve(filename);
        if (!Files.exists(filePath)) {
            Logger.println("File does not exist: " + filename, 1);
            throw new FileNotFoundException("Cache file not found: " + filePath);
        }

        Logger.println("Read file: " + filename + " successfully!", 3);
        return Files.readAllBytes(filePath);
    }

    public static Path getAbsolutePath(String filename) throws IOException {
        return XDG_CACHE_DIR.resolve(filename);
    }

    public static boolean write(String filename, byte[] data) throws IOException {
        Path filePath = XDG_CACHE_DIR.resolve(filename);

        try {
            Files.write(filePath, data);
            return true;
        } catch (IOException e) {
            Logger.println("Error writing cache file: " + e.getMessage());
            return false;
        }
    }

    private static Path getXDGCacheDir() {
        String cacheHome = System.getenv("XDG_CACHE_HOME");
        if (cacheHome == null) {
            cacheHome = System.getProperty("user.home") + "/.cache";
        }
        Path cacheDir = Path.of(cacheHome, "Spotify-Stat-Tracker");
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            Logger.println(e);
            throw new RuntimeException(e);
        }

        return cacheDir;
    }
}
