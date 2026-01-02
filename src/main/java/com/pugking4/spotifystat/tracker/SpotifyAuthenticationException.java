package com.pugking4.spotifystat.tracker;

public class SpotifyAuthenticationException extends SpotifyException {
    private final int statusCode;
    public SpotifyAuthenticationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
    public int getStatusCode() { return statusCode; }
}

