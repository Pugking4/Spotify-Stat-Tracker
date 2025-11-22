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
    private static final Path CODE_FILE = Paths.get("oauth_code.txt");
    private static String code;
    private static String state;

    public static void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/callback", new CallbackHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:" + PORT + "/callback");
        System.out.println("path is: " + CODE_FILE.toString());
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
