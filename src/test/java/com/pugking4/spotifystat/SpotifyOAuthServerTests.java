package com.pugking4.spotifystat;

import com.pugking4.spotifystat.tracker.Cache;
import io.github.cdimascio.dotenv.Dotenv;
import org.mockito.Mock;

import java.net.http.HttpClient;

public class SpotifyOAuthServerTests {
    @Mock
    private HttpClient httpClient;
    @Mock
    private Dotenv dotenv;
    @Mock
    private Cache cache;

}
