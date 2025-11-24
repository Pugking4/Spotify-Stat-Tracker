
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

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
import java.util.HashMap;
import java.util.Map;
public class SpotifyOAuthServer {
    private static final int PORT = 8888;
    private static final Path CODE_FILE = Paths.get("./tracker/resources/oauth_code.txt");
    private static String code;
    private static String state;

    public static void startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("[REDACTED]", PORT), 0);
            server.createContext("/callback", new CallbackHandler());
            server.setExecutor(null);
            server.start();
            Logger.println("Server started on " + server.getAddress() + ":" + PORT + "/callback");
        } catch (IOException e) {
            Logger.println(e);
            Logger.log("Failed to start server", e);
            throw new RuntimeException(e);
        }
    }

    static class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            URI requestURI = exchange.getRequestURI();
            Map<String, String> params = queryToMap(requestURI.getQuery());

            code = params.get("code");
            state = params.get("state");

            if (code != null) {
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(CODE_FILE))) {
                    writer.println(code);
                } catch (IOException e) {
                    Logger.println(e);
                    Logger.log("Failed to write code", e);
                }
            }

            try {
                String response = "Authorization code received: " + code + "<br>State: " + state;
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (IOException e) {
                Logger.println(e);
                Logger.log("Failed send server response", e);
            }

            // Stop the server after receiving the code
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
