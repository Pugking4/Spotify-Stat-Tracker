package com.pugking4.spotifytracker.dto;

import java.time.Instant;
public record PlayedTrack(Track track, String contextType, Device device, Integer currentPopularity, Instant time_played) {}
