package ru.example.vkchat.reputation;

import ru.example.vkchat.VKChatPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class ReputationManager {
    private final VKChatPlugin plugin;
    private final ru.example.vkchat.database.SQLCompat sqlCompat;
    private boolean enabled;

    public ReputationManager(VKChatPlugin plugin) {
        this.plugin = plugin;
        this.sqlCompat = new ru.example.vkchat.database.SQLCompat(plugin);
        this.enabled = plugin.getConfig().getBoolean("reputation.enabled", true);
    }

    public void addMessage(int vkId, String message) {
        if (!enabled) return;
        if (message.length() < plugin.getConfig().getInt("reputation.min-message-length", 5)) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement check = conn.prepareStatement("SELECT last_message FROM vkchat_reputation WHERE vk_id = ?")) {
                check.setInt(1, vkId);
                ResultSet rs = check.executeQuery();

                long lastTime = 0;
                boolean exists = false;
                if (rs.next()) {
                    exists = true;
                    lastTime = rs.getLong("last_message");
                }

                long now = System.currentTimeMillis();
                int cooldown = plugin.getConfig().getInt("reputation.cooldown-seconds", 30) * 1000;

                if (now - lastTime > cooldown) {
                    int add = plugin.getConfig().getInt("reputation.message-reward", 1);

                    // [FIX] Работает и для SQLite и для MySQL
                    if (exists) {
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE vkchat_reputation SET points = points + ?, last_message = ? WHERE vk_id = ?")) {
                            update.setInt(1, add);
                            update.setLong(2, now);
                            update.setInt(3, vkId);
                            update.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement insert = conn.prepareStatement(
                                "INSERT INTO vkchat_reputation (vk_id, points, last_message) VALUES (?, ?, ?)")) {
                            insert.setInt(1, vkId);
                            insert.setInt(2, add);
                            insert.setLong(3, now);
                            insert.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
            }
        });
    }
    
    public int getPoints(int vkId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT points FROM vkchat_reputation WHERE vk_id = ?");
            ps.setInt(1, vkId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("points");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
        }
        return 0;
    }
    
    public void deductPoints(int vkId, int amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                // [FIX] Атомарная операция вместо SELECT + UPDATE
                PreparedStatement ps;
                if (plugin.getConfig().getBoolean("database.use-mysql", false)) {
                    ps = conn.prepareStatement("UPDATE vkchat_reputation SET points = GREATEST(0, points - ?) WHERE vk_id = ?");
                } else {
                    ps = conn.prepareStatement("UPDATE vkchat_reputation SET points = MAX(0, points - ?) WHERE vk_id = ?");
                }
                ps.setInt(1, amount);
                ps.setInt(2, vkId);
                ps.executeUpdate();
                
                // Получаем новое значение для события
                int newPoints = getPoints(vkId);
                plugin.getServer().getPluginManager().callEvent(new ru.example.vkchat.api.events.ReputationChangeEvent(vkId, newPoints + amount, newPoints));
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
            }
        });
    }
    
    public void takeReputation(int vkId, int amount) {
        deductPoints(vkId, amount);
    }
    
    public void addPoints(int vkId, int amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                int oldPoints = getPoints(vkId);
                int newPoints = oldPoints + amount;
                
                PreparedStatement ps;
                if (plugin.getConfig().getBoolean("database.use-mysql", false)) {
                    ps = conn.prepareStatement("INSERT INTO vkchat_reputation (vk_id, points) VALUES (?, ?) ON DUPLICATE KEY UPDATE points = points + ?");
                } else {
                    ps = conn.prepareStatement("INSERT INTO vkchat_reputation (vk_id, points) VALUES (?, ?) ON CONFLICT(vk_id) DO UPDATE SET points = points + ?");
                }
                ps.setInt(1, vkId);
                ps.setInt(2, amount);
                ps.setInt(3, amount);
                ps.executeUpdate();
                
                plugin.getServer().getPluginManager().callEvent(new ru.example.vkchat.api.events.ReputationChangeEvent(vkId, oldPoints, newPoints));
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
            }
        });
    }

    public void setPoints(int vkId, int amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                int oldPoints = getPoints(vkId);
                
                PreparedStatement ps;
                if (plugin.getConfig().getBoolean("database.use-mysql", false)) {
                    ps = conn.prepareStatement("INSERT INTO vkchat_reputation (vk_id, points) VALUES (?, ?) ON DUPLICATE KEY UPDATE points = ?");
                } else {
                    ps = conn.prepareStatement("INSERT INTO vkchat_reputation (vk_id, points) VALUES (?, ?) ON CONFLICT(vk_id) DO UPDATE SET points = ?");
                }
                ps.setInt(1, vkId);
                ps.setInt(2, amount);
                ps.setInt(3, amount);
                ps.executeUpdate();
                
                plugin.getServer().getPluginManager().callEvent(new ru.example.vkchat.api.events.ReputationChangeEvent(vkId, oldPoints, amount));
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
            }
        });
    }

    public boolean claimDailyBonus(int vkId, int amount) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT last_bonus FROM vkchat_reputation WHERE vk_id = ?");
            ps.setInt(1, vkId);
            ResultSet rs = ps.executeQuery();
            
            long lastBonus = 0;
            boolean exists = false;
            if (rs.next()) {
                exists = true;
                lastBonus = rs.getLong("last_bonus");
            }
            
            long now = System.currentTimeMillis();
            if (now - lastBonus > 86400000L) { // 24 часа
                if (exists) {
                    PreparedStatement update = conn.prepareStatement("UPDATE vkchat_reputation SET last_bonus = ? WHERE vk_id = ?");
                    update.setLong(1, now);
                    update.setInt(2, vkId);
                    update.executeUpdate();
                } else {
                    PreparedStatement insert = conn.prepareStatement("INSERT INTO vkchat_reputation (vk_id, last_bonus) VALUES (?, ?)");
                    insert.setInt(1, vkId);
                    insert.setLong(2, now);
                    insert.executeUpdate();
                }
                addPoints(vkId, amount);
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
        }
        return false;
    }

    public long getFestivalEndTime(String nation) {
        return 0L;
    }

    public void setFestivalEndTime(String nation, long time) {
    }

    // Сохраним функционал промокодов через БД
    // Сохраним функционал промокодов через БД
    public void createPromo(String code, int reward, int uses) {
        code = code.toUpperCase().trim();
        final String finalCode = code;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement ps;
                boolean useMysql = plugin.getConfig().getBoolean("database.use-mysql", false);
                if (useMysql) {
                    ps = conn.prepareStatement(
                        "INSERT INTO vkchat_promocodes (code, reward, max_uses, current_uses) VALUES (?, ?, ?, 0) " +
                        "ON DUPLICATE KEY UPDATE reward = ?, max_uses = ?");
                } else {
                    ps = conn.prepareStatement(
                        "INSERT INTO vkchat_promocodes (code, reward, max_uses, current_uses) VALUES (?, ?, ?, 0) " +
                        "ON CONFLICT(code) DO UPDATE SET reward = excluded.reward, max_uses = excluded.max_uses");
                }
                ps.setString(1, finalCode);
                ps.setInt(2, reward);
                ps.setInt(3, uses);
                if (useMysql) {
                    ps.setInt(4, reward);
                    ps.setInt(5, uses);
                }
                ps.executeUpdate();
                plugin.getLogger().info("✓ Успешно создан промокод в БД: " + finalCode + " на " + reward + " репутации (" + uses + " использ.)");
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
            }
        });
    }

    public String usePromo(int vkId, String code) {
        code = code.toUpperCase().trim();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Проверим, существует ли промокод
            PreparedStatement ps = conn.prepareStatement("SELECT reward, max_uses, current_uses FROM vkchat_promocodes WHERE code = ?");
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return "❌ Промокод не найден!";
            }
            
            int reward = rs.getInt("reward");
            int maxUses = rs.getInt("max_uses");
            int currentUses = rs.getInt("current_uses");
            if (currentUses >= maxUses) {
                return "❌ Лимит использования промокода полностью исчерпан!";
            }
            
            // Проверим, активировал ли его данный игрок ранее
            PreparedStatement check = conn.prepareStatement("SELECT 1 FROM vkchat_promo_activations WHERE code = ? AND vk_id = ?");
            check.setString(1, code);
            check.setInt(2, vkId);
            ResultSet rsCheck = check.executeQuery();
            if (rsCheck.next()) {
                return "❌ Вы уже активировали этот промокод ранее!";
            }
            
            // Запишем активацию
            PreparedStatement insertAct = conn.prepareStatement("INSERT INTO vkchat_promo_activations (code, vk_id) VALUES (?, ?)");
            insertAct.setString(1, code);
            insertAct.setInt(2, vkId);
            insertAct.executeUpdate();
            
            // Увеличим счетчик использований
            PreparedStatement updateCount = conn.prepareStatement("UPDATE vkchat_promocodes SET current_uses = current_uses + 1 WHERE code = ?");
            updateCount.setString(1, code);
            updateCount.executeUpdate();
            
            // Выдаем очки
            addPoints(vkId, reward);
            return "✅ Успех! Промокод '" + code + "' успешно активирован! Начислено: +" + reward + " репутации ВК!";
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
            return "❌ Ошибка базы данных при активации промокода!";
        }
    }

    public String getTopReputation() {
        Map<Integer, Integer> scores = new HashMap<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT vk_id, points FROM vkchat_reputation ORDER BY points DESC LIMIT 10");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                scores.put(rs.getInt("vk_id"), rs.getInt("points"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
        }
        
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : list) {
            sb.append(count + 1).append(". @id").append(entry.getKey()).append(" - ").append(entry.getValue()).append(" очков\n");
            count++;
        }
        if (sb.length() > 0) {
            sb.append("\n Всего участников: ").append(getTotalPlayers());
        }
        return sb.length() == 0 ? "Нет данных" : sb.toString().trim();
    }

    public int getRank(int vkId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            int myPoints = getPoints(vkId);
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as rank FROM vkchat_reputation WHERE points > ?");
            ps.setInt(1, myPoints);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("rank") + 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
        }
        return 1;
    }

    public int getTotalPlayers() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as total FROM vkchat_reputation");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
        }
        return 0;
    }

    public String getTopOnePlayerName() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT vk_id, points FROM vkchat_reputation ORDER BY points DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int vkId = rs.getInt("vk_id");
                int pts = rs.getInt("points");
                org.json.JSONObject user = plugin.getVkManager().getUserInfo(vkId);
                if (user != null) {
                    return user.getString("first_name") + " " + user.getString("last_name") + " (" + pts + " pts)";
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка БД репутации: " + e.getMessage());
        }
        return null;
    }
}
