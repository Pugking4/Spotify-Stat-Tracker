package com.pugking4.spotifystat.tracker;

public class SpotifyApiException extends SpotifyException {
    private final int statusCode;
    public SpotifyApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
    public int getStatusCode() { return statusCode; }
}
