package com.pugking4.spotifystat.tracker;

import java.io.IOException;

public interface Cache {
    byte[] read(String filename) throws IOException;
    void write(String filename, byte[] data) throws IOException;
}

