package com.pugking4.spotifystat.tracker;

import com.pugking4.spotifystat.common.logging.Logger;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.cdimascio.dotenv.Dotenv;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.*;
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
    private static final int PORT = 8888;
    static Dotenv dotenv = Dotenv.configure().directory(".").load();
    private static final String KEYSTORE_FILENAME = "./resources/keystore.jks"; // your keystore file path
    private static final String KEYSTORE_PASSWORD = dotenv.get("KEYSTORE_PASSWORD"); // your keystore password
    private static final String IP_ADDRESS = dotenv.get("IP_ADDRESS");

    private static final String OAUTH_FILENAME = "oauth_code.txt";
    private static String code;
    private static String state;


    public static void startServer() {
        try {
            // Load the keystore
            Path keystorePath = CacheUtilities.getAbsolutePath("keystore.jks");
            KeyStore ks = KeyStore.getInstance("JKS");

            try (InputStream ksStream = Files.newInputStream(keystorePath)) {
                ks.load(ksStream, KEYSTORE_PASSWORD.toCharArray());
            } catch (IOException e) {
                Logger.println("Keystore not found, generating self-signed cert...", 2);
                generateKeystore(keystorePath);
                try (InputStream ksStream = Files.newInputStream(keystorePath)) {
                    ks.load(ksStream, KEYSTORE_PASSWORD.toCharArray());
                }
            }

            // Set up key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

            // Initialize SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Create HttpsServer
            HttpsServer server = HttpsServer.create(new InetSocketAddress(IP_ADDRESS, PORT), 0);

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
            System.out.println("HTTPS Server started on https://" + server.getAddress() + ":" + PORT + "/callback");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to start HTTPS server", e);
        }
    }

    private static void generateKeystore(Path keystorePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "spotifystat",
                "-keyalg", "RSA", "-keysize", "2048",
                "-keystore", keystorePath.toString(),
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=SpotifyStatServer",
                "-validity", "365",
                "-ext", "SAN=dns:localhost,dns:127.0.0.1,ip:127.0.0.1,ip:" + IP_ADDRESS
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


    static class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            Map<String, String> params = queryToMap(requestURI.getQuery());

            code = params.get("code");
            state = params.get("state");

            Logger.println("Code has been retrieved!", 3);
            if (code != null) {
                Logger.println("code: " + code, 3);
                CacheUtilities.write(OAUTH_FILENAME, code.getBytes(StandardCharsets.UTF_8));
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
