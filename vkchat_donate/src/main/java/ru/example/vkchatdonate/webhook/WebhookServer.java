package ru.example.vkchatdonate.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import ru.example.vkchatdonate.DonateManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * WebhookServer — embedded HTTP server for instant DonatePay webhook processing.
 * Replaces 30s polling when webhook.enabled=true.
 */
public class WebhookServer {

    private final DonateManager donateManager;
    private final int port;
    private final String secret;
    private HttpServer server;

    public WebhookServer(DonateManager donateManager, int port, String secret) {
        this.donateManager = donateManager;
        this.port = port;
        this.secret = secret;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/webhook/donate", new DonateWebhookHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        donateManager.getPlugin().getLogger().info("[Webhook] Сервер запущен на порту " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            donateManager.getPlugin().getLogger().info("[Webhook] Сервер остановлен");
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    private class DonateWebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            // Verify secret from header
            String headerToken = exchange.getRequestHeaders().getFirst("X-Webhook-Secret");
            if (headerToken == null || !headerToken.equals(secret)) {
                sendResponse(exchange, 403, "{\"error\":\"Invalid secret\"}");
                return;
            }

            // Read body
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"Cannot read body\"}");
                return;
            }

            // Parse JSON
            JSONObject json;
            try {
                json = new JSONObject(body);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"Invalid JSON\"}");
                return;
            }

            // Extract fields
            int txId = json.optInt("id", 0);
            double amount = json.optDouble("amount", 0);
            String status = json.optString("status", "");
            String sender = json.optString("what", "").trim();
            String comment = json.optString("comment", "").trim();

            if (!"success".equals(status)) {
                sendResponse(exchange, 200, "{\"ok\":true,\"skipped\":\"not success\"}");
                return;
            }

            // Process donation on main thread
            donateManager.getPlugin().getServer().getScheduler().runTask(
                    donateManager.getPlugin(),
                    () -> donateManager.processDonation(txId, amount, sender, comment)
            );

            sendResponse(exchange, 200, "{\"ok\":true}");
        }

        private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
