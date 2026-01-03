package com.pugking4.spotifystat.tracker;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;

public record OAuthServerConfig(String host, int port, String keystorePassword, Path keystorePath) {
    public OAuthServerConfig {
        Objects.requireNonNull(host, "HOST is required");
        if (host.isBlank()) throw new IllegalArgumentException("HOST is blank");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("PORT out of range: " + port);

        InetSocketAddress addr = new InetSocketAddress(host, port);
        if (addr.isUnresolved()) throw new IllegalArgumentException("Host cannot be resolved: " + host);

        Objects.requireNonNull(keystorePassword, "Keystore password is required");
        if (keystorePassword.isBlank()) throw new IllegalArgumentException("Keystore password is blank");

        Objects.requireNonNull(keystorePath, "Keystore path is required");
        if (!keystorePath.getFileName().toString().endsWith(".jks")) throw new IllegalArgumentException("Keystore path must point to a file ending in .jks");
        File keystoreFile = new File(keystorePath.toUri());
        if (keystoreFile.isDirectory()) throw new IllegalArgumentException("Keystore path must point to a .jks file, found a directory");
    }

    public OAuthServerConfig(String host, int port, String keystorePassword) {
        this(host, port, keystorePassword, FileCache.getAbsolutePath("keystore.jks"));
    }
}
