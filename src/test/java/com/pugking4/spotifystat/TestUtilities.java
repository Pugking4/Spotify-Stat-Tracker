package com.pugking4.spotifystat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TestUtilities {
    public static String loadResource(String filename) {
        try (InputStream is = TestUtilities.class.getResourceAsStream("/" + filename)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
