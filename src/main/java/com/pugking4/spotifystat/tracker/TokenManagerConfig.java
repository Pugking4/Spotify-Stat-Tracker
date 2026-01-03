package com.pugking4.spotifystat.tracker;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record TokenManagerConfig(String clientId, String clientSecret, String redirectUri) {
    public String authorisationHeaderValue() {
        return Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }
}
