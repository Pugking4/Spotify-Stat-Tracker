import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

public class SpotifyOAuthServer {
    private static final int PORT = 8888;
    private static final String KEYSTORE_PATH = "./tracker/resources/keystore.jks"; // your keystore file path
    private static final String KEYSTORE_PASSWORD = "password"; // your keystore password
    private static final Path CODE_FILE = Paths.get("./tracker/resources/oauth_code.txt");
    private static String code;
    private static String state;

    public static void startServer() {
        try {
            // Load the keystore
            KeyStore ks = KeyStore.getInstance("JKS");
            FileInputStream fis = new FileInputStream(KEYSTORE_PATH);
            ks.load(fis, KEYSTORE_PASSWORD.toCharArray());

            // Set up key manager factory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

            // Initialize SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Create HttpsServer
            HttpsServer server = HttpsServer.create(new InetSocketAddress("[REDACTED]", PORT), 0);

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

    static class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            Map<String, String> params = queryToMap(requestURI.getQuery());

            code = params.get("code");
            state = params.get("state");

            if (code != null) {
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(CODE_FILE))) {
                    writer.println(code);
                }
            }

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
