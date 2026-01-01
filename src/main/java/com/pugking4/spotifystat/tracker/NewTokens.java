package com.pugking4.spotifystat.tracker;

import java.time.Instant;

public record NewTokens(String accessToken, String refreshToken, Instant expiry) {}
