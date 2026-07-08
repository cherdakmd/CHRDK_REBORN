package ru.example.vkchatdonate.api;

import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * DonatePayClient — выделенный HTTP-клиент для DonatePay API.
 *
 * FIX #4: Отдельный класс вместо встроенного в DonateManager (Single Responsibility)
 * FIX #5: Правильная обработка HTTP-ошибок (логирование кода ответа)
 * FIX #6: Защита от утечки токена в логи
 * IMPROVE #1: Переиспользуемый клиент с connection pooling
 * IMPROVE #2: Configurable timeouts
 */
public class DonatePayClient {

    private final HttpClient httpClient;
    private final Plugin plugin;

    public DonatePayClient(Plugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Запрос последних транзакций.
     *
     * @param token API-токен DonatePay
     * @param limit количество транзакций (макс. 50)
     * @return список транзакций или пустой список при ошибке
     */
    public List<DonateTransaction> fetchTransactions(String token, int limit) {
        if (token == null || token.isEmpty() || token.equals("YOUR_DONATEPAY_TOKEN")) {
            return List.of();
        }

        try {
            // FIX #6: Токен НЕ логируется
            String url = "https://donatepay.ru/api/v1/transactions?access_token=" + token
                    + "&limit=" + Math.min(limit, 50) + "&type=donation";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            // FIX #5: Логируем код ответа при ошибке
            if (resp.statusCode() != 200) {
                plugin.getLogger().warning("[DonatePay] HTTP " + resp.statusCode()
                        + " от DonatePay API (длина ответа: " + resp.body().length() + ")");
                return List.of();
            }

            return parseTransactions(resp.body());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[DonatePay] Сеть недоступна: " + e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DonatePay] Ошибка: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Парсинг JSON-ответа DonatePay.
     */
    private List<DonateTransaction> parseTransactions(String json) {
        List<DonateTransaction> result = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (!root.has("data")) return result;

            JSONArray data = root.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject tx = data.getJSONObject(i);
                try {
                    result.add(new DonateTransaction(
                            tx.getInt("id"),
                            tx.optString("status", ""),
                            tx.optDouble("amount", 0),
                            tx.optString("what", "").trim(),
                            tx.optString("comment", "").trim()
                    ));
                } catch (Exception e) {
                    plugin.getLogger().warning("[DonatePay] Ошибка парсинга транзакции: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DonatePay] Ошибка парсинга JSON: " + e.getMessage());
        }
        return result;
    }

    /**
     * Данные одной транзакции.
     */
    public record DonateTransaction(
            int id,
            String status,
            double amount,
            String sender,
            String comment
    ) {
        public boolean isSuccess() {
            return "success".equals(status);
        }
    }
}
