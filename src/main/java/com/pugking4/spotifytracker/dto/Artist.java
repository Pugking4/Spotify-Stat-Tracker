package com.pugking4.spotifytracker.dto;

import java.time.Instant;
import java.util.List;

public record Artist(String id, String name, Integer followers, List<String> genres, String image, Integer popularity, Instant updatedAt) {}
