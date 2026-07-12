package ru.example.vkchat.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.logging.Logger;

/**
 * Минимальный HTTP-сервер для раздачи ресурспака.
 * Отдаёт ZIP-файл из plugins/VKChat/resourcepack/ по HTTP.
 */
public class ResourcePackServer {
    private final JavaPlugin plugin;
    private final int port;
    private HttpServer server;
    private byte[] zipBytes;
    private String sha1Hash;
    private String url;

    public ResourcePackServer(JavaPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    public void start() {
        Logger log = plugin.getLogger();
        try {
            File packDir = new File(plugin.getDataFolder(), "resourcepack");
            if (!packDir.exists()) packDir.mkdirs();

            File packFile = new File(packDir, "CHRDK_REBORN_Resourcepack.zip");
            if (!packFile.exists()) {
                log.warning("[ResourcePack] Файл не найден: " + packFile.getAbsolutePath());
                log.warning("[ResourcePack] Положите ZIP в plugins/VKChat/resourcepack/");
                return;
            }

            zipBytes = java.nio.file.Files.readAllBytes(packFile.toPath());
            sha1Hash = calcSha1(zipBytes);
            String host = detectHost();
            url = "http://" + host + ":" + port + "/resourcepack.zip";

            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/resourcepack.zip", new ResourcePackHandler());
            server.setExecutor(null);
            server.start();

            log.info("[ResourcePack] HTTP-сервер запущен на порту " + port);
            log.info("[ResourcePack] URL: " + url);
            log.info("[ResourcePack] SHA-1: " + sha1Hash);
            log.info("[ResourcePack] Размер: " + (zipBytes.length / 1024) + " KB");
        } catch (Exception e) {
            log.severe("[ResourcePack] Не удалось запустить HTTP-сервер: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("[ResourcePack] HTTP-сервер остановлен.");
        }
    }

    public String getUrl() { return url; }
    public String getHash() { return sha1Hash; }

    private String detectHost() {
        // 1. config.yml → resource-pack.host (ручное указание)
        String cfgHost = plugin.getConfig().getString("resource-pack.host", "").trim();
        if (!cfgHost.isEmpty()) return cfgHost;

        // 2. server.properties → server-ip
        try {
            File sp = new File("server.properties");
            if (sp.exists()) {
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(sp)) {
                    props.load(fis);
                }
                String spIp = props.getProperty("server-ip", "").trim();
                if (!spIp.isEmpty()) return spIp;
            }
        } catch (Exception ignored) {}

        // 3. network interfaces — ищем не-loopback IPv4
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "0.0.0.0";
    }

    private class ResourcePackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"CHRDK_REBORN_Resourcepack.zip\"");
            exchange.sendResponseHeaders(200, zipBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(zipBytes);
            }
        }
    }

    private static String calcSha1(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
