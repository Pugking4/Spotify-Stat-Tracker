package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;
import com.sun.net.httpserver.*;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public class SpotifyOAuthServer {
    private final Cache cache;
    private final HttpsServer server;
    private final OAuthServerConfig cfg;

    public SpotifyOAuthServer(Cache cache, OAuthServerConfig cfg) {
        this.cache = cache;
        this.cfg = cfg;
        try {
            this.server = HttpsServer.create(new InetSocketAddress(cfg.host(), cfg.port()), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SpotifyOAuthServer(Cache cache, OAuthServerConfig cfg, HttpsServer server) {
        this.cache = cache;
        this.cfg = cfg;
        this.server = server;
    }

    public HttpHandler callbackHandler() { return new CallbackHandler(); }

    public void startServer() {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            String keystorePassword = cfg.keystorePassword();
            Path keystorePath = cfg.keystorePath();

            try (InputStream ksStream = Files.newInputStream(keystorePath)) {
                ks.load(ksStream, keystorePassword.toCharArray());
            } catch (IOException e) {
                Logger.println("Keystore not found, generating self-signed cert...", 2);
                generateKeystore(keystorePath, keystorePassword);
                try (InputStream ksStream = Files.newInputStream(keystorePath)) {
                    ks.load(ksStream, keystorePassword.toCharArray());
                }
            }

            // Set up key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keystorePassword.toCharArray());

            // Initialize SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Configure HTTPS parameters
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });

            // Create context and start server
            server.createContext("/callback", new CallbackHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            Logger.println("HTTPS Server started on https://" + server.getAddress() + ":" + server.getAddress().getPort() + "/callback", 1);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to start HTTPS server", e);
        }
    }

    public void stopServer() {
        try {
            server.stop(0);
        } catch (Exception ignored) { }
    }


    public int getPort() {
        return server.getAddress().getPort();
    }

    private void generateKeystore(Path keystorePath, String keystorePassword) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "spotifystat",
                "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", keystorePath.toString(),
                "-storepass", keystorePassword,
                "-keypass", keystorePassword,
                "-dname", "CN=SpotifyStatServer",
                "-validity", "365",
                "-ext", "SAN=dns:localhost,dns:127.0.0.1,ip:127.0.0.1,ip:" + cfg.host()
        );

        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            RuntimeException e = new RuntimeException("keytool failed with exit code: " + exitCode);
            Logger.log("keytool failed with exit code", e);
            throw e;
        }

        Logger.println("Keystore generated at: " + keystorePath.toAbsolutePath(), 2);
    }


    class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            Map<String, String> params = queryToMap(requestURI.getQuery());

            String code = params.get("code");
            String state = params.get("state");

            Logger.println("Code has been retrieved!", 3);
            if (code != null) {
                Logger.println("code: " + code, 3);
                String OAUTH_FILENAME = "oauth_code.txt";
                cache.write(OAUTH_FILENAME, code.getBytes(StandardCharsets.UTF_8));
            }
            Logger.println("Wrote code!", 3);

            String response = "Authorization code received: " + code + "<br>State: " + state;
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();

            // Stop server after receiving code
            exchange.getHttpContext().getServer().stop(0);
        }
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], URLDecoder.decode(entry[1], StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }
}
