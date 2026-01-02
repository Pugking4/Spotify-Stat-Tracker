package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileCache implements Cache {
    private static final Path XDG_CACHE_DIR = getXDGCacheDir();

    @Override
    public byte[] read(String filename) throws IOException {
        Logger.println("Reading: " + filename, 3);
        Path filePath = XDG_CACHE_DIR.resolve(filename);
        if (!Files.exists(filePath)) {
            Logger.println("Not found: " + filename, 1);
            throw new FileNotFoundException("Cache missing: " + filePath);
        }
        Logger.println("Read OK: " + filename, 3);
        return Files.readAllBytes(filePath);
    }

    @Override
    public void write(String filename, byte[] data) throws IOException {
        Path filePath = XDG_CACHE_DIR.resolve(filename);
        Files.write(filePath, data);
        Logger.println("Wrote: " + filename, 3);
    }

    public static Path getAbsolutePath(String filename) {
        return XDG_CACHE_DIR.resolve(filename);
    }

    private static Path getXDGCacheDir() {
        String cacheHome = System.getenv("XDG_CACHE_HOME");
        if (cacheHome == null) cacheHome = System.getProperty("user.home") + "/.cache";
        Path cacheDir = Path.of(cacheHome, "Spotify-Stat-Tracker");
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return cacheDir;
    }
}
