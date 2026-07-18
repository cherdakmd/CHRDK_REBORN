package ru.example.vkchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchat.VKChatPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

public class AdminDashboard implements CommandExecutor, Listener {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&',
            "\u00a78\u25b8 \u00a74\u00a7l\u0410\u0414\u041c\u0418\u041d \u00a78\u25c4 \u00a77\u041f\u0430\u043d\u0435\u043b\u044c");
    private static final int SIZE = 54;
    private static final UUID HOLDER_UUID = UUID.fromString("00000000-0000-0000-0000-0000000000AD");

    private final VKChatPlugin plugin;

    private final Set<UUID> awaitingBroadcast = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingPlayerSearch = ConcurrentHashMap.newKeySet();

    public AdminDashboard(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "\u042d\u0442\u0443 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 \u043c\u043e\u0436\u0435\u0442 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u044c \u0442\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("vkchat.admin")) {
            player.sendMessage(ChatColor.RED + "\u041d\u0435\u0442 \u043f\u0440\u0430\u0432.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("dashboard")) {
            openDashboard(player);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            player.sendMessage(ChatColor.GREEN + "\u2713 VKChat \u043a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044f \u043f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u0430!");
            return true;
        }
        openDashboard(player);
        return true;
    }

    public void openDashboard(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminHolder(), SIZE, TITLE);

        ItemStack border = glass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = glass(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < SIZE; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            } else if (inv.getItem(i) == null) {
                inv.setItem(i, accent);
            }
        }

        inv.setItem(4, headerItem(Material.RED_STAINED_GLASS_PANE, "\u00a74\u00a7l\u041f\u0410\u041d\u0415\u041b\u042c \u0423\u041f\u0420\u0410\u0412\u041b\u0415\u041d\u0418\u042f"));

        inv.setItem(10, serverStatsItem());
        inv.setItem(11, economyItem());
        inv.setItem(12, nationOverviewItem());
        inv.setItem(13, recentLoginsItem());
        inv.setItem(14, warningsBansItem());

        inv.setItem(28, broadcastItem());
        inv.setItem(29, reloadItem());
        inv.setItem(30, maintenanceItem());
        inv.setItem(31, backupItem());

        inv.setItem(33, playerSearchItem());

        inv.setItem(49, closeItem());

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        if (!(e.getInventory().getHolder() instanceof AdminHolder)) return;
        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;
        int slot = e.getRawSlot();

        switch (slot) {
            case 10:
                showServerStats(player);
                break;
            case 11:
                showEconomy(player);
                break;
            case 12:
                showNations(player);
                break;
            case 13:
                showRecentLogins(player);
                break;
            case 14:
                showWarningsBans(player);
                break;
            case 28:
                startBroadcast(player);
                break;
            case 29:
                doReload(player);
                break;
            case 30:
                toggleMaintenance(player);
                break;
            case 31:
                triggerBackup(player);
                break;
            case 33:
                startPlayerSearch(player);
                break;
            case 49:
                player.closeInventory();
                break;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (awaitingBroadcast.remove(uuid)) {
            String message = ChatColor.translateAlternateColorCodes('&', e.getMessage());
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                String prefix = ChatColor.translateAlternateColorCodes('&',
                        "&4&l[\u0410\u0414\u041c\u0418\u041d]&r ");
                Bukkit.broadcastMessage(prefix + message);
                player.sendMessage(ChatColor.GREEN + "\u2713 \u0421\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435 \u0440\u043e\u0441\u0441\u043b\u0430\u043d\u043e!");
            });
            return;
        }

        if (awaitingPlayerSearch.remove(uuid)) {
            e.setCancelled(true);
            String targetName = e.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin, () -> showPlayerInfo(player, targetName));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INFO ITEMS (hover only, no click action)
    // ═══════════════════════════════════════════════════════════════

    private void showServerStats(Player player) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double tps = getTps();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptimeStr = formatUptime(uptime);
        int todayJoins = plugin.getStatsManager().getTodayJoins();
        int totalJoins = plugin.getStatsManager().getTotalJoins();

        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&4&l[\u0421\u0422\u0410\u0422\u042b \u0421\u0415\u0420\u0412\u0415\u0420\u0410]"));
        player.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        player.sendMessage(ChatColor.GREEN + " \u25b6 \u041e\u043d\u043b\u0430\u0439\u043d: " + ChatColor.YELLOW + online + ChatColor.GRAY + "/" + ChatColor.WHITE + max);
        player.sendMessage(ChatColor.GREEN + " \u25b6 TPS: " + ChatColor.YELLOW + String.format("%.1f", tps) + (tps >= 18 ? ChatColor.GREEN + " \u2713" : tps >= 15 ? ChatColor.YELLOW + " \u26a0" : ChatColor.RED + " \u2717"));
        player.sendMessage(ChatColor.GREEN + " \u25b6 \u0410\u043f\u0442\u0430\u0439\u043c: " + ChatColor.YELLOW + uptimeStr);
        player.sendMessage(ChatColor.GREEN + " \u25b6 \u0417\u0430\u0445\u043e\u0434\u043e\u0432 \u0441\u0435\u0433\u043e\u0434\u043d\u044f: " + ChatColor.YELLOW + todayJoins);
        player.sendMessage(ChatColor.GREEN + " \u25b6 \u0412\u0441\u0435\u0433\u043e \u0437\u0430\u0445\u043e\u0434\u043e\u0432: " + ChatColor.YELLOW + totalJoins);
        player.sendMessage("");
    }

    private void showEconomy(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                PreparedStatement topPs = conn.prepareStatement(
                        "SELECT vk_id, points FROM vkchat_reputation ORDER BY points DESC LIMIT 1");
                ResultSet topRs = topPs.executeQuery();
                String topName = "\u041d\u0435\u0442 \u0434\u0430\u043d\u043d\u044b\u0445";
                int topPoints = 0;
                if (topRs.next()) {
                    topPoints = topRs.getInt("points");
                    int vkId = topRs.getInt("vk_id");
                    org.json.JSONObject user = plugin.getVkManager().getUserInfo(vkId);
                    if (user != null) {
                        topName = user.getString("first_name") + " " + user.getString("last_name");
                    } else {
                        topName = "VK#" + vkId;
                    }
                }

                String finalTopName = topName;
                int finalTopPoints = topPoints;

                PreparedStatement totalPs = conn.prepareStatement("SELECT COALESCE(SUM(points), 0) as total FROM vkchat_reputation");
                ResultSet totalRs = totalPs.executeQuery();
                int totalCirculation = totalRs.next() ? totalRs.getInt("total") : 0;

                PreparedStatement countPs = conn.prepareStatement("SELECT COUNT(*) as cnt FROM vkchat_reputation WHERE points > 0");
                ResultSet countRs = countPs.executeQuery();
                int holders = countRs.next() ? countRs.getInt("cnt") : 0;

                final int finalTotal = totalCirculation;
                final int finalHolders = holders;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&a&l[\u042d\u041a\u041e\u041d\u041e\u041c\u0418\u041a\u0410]"));
                    player.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
                    player.sendMessage(ChatColor.GREEN + " \u25b6 \u0422\u043e\u043f \u0440\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u0438: " + ChatColor.GOLD + finalTopName);
                    player.sendMessage(ChatColor.GREEN + " \u25b6 \u0411\u0430\u043b\u043b\u0430\u043d\u0441 \u0442\u043e\u043f\u0430: " + ChatColor.YELLOW + finalTopPoints + " \u0440\u0435\u043f.");
                    player.sendMessage(ChatColor.GREEN + " \u25b6 \u0412 \u043e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u0438: " + ChatColor.YELLOW + finalTotal + " \u0440\u0435\u043f.");
                    player.sendMessage(ChatColor.GREEN + " \u25b6 \u0412\u043b\u0430\u0434\u0435\u043b\u044c\u0446\u0435\u0432: " + ChatColor.YELLOW + finalHolders);
                    player.sendMessage("");
                });
            } catch (SQLException ex) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "\u041e\u0448\u0438\u0431\u043a\u0430 \u0411\u0414: " + ex.getMessage());
                });
            }
        });
    }

    private void showNations(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b&l[\u041d\u0410\u0426\u0418\u0418]"));
        player.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        String[] nations = {"\u0425\u0440\u0430\u043d\u0442\u044b", "\u0421\u043f\u0438\u0440\u0438\u0442\u044b", "\u041c\u0435\u0445\u0430\u043d\u0438\u043a\u0438", "\u041c\u0430\u0433\u0438", "\u041d\u0435\u043a\u0440\u043e\u043c\u0430\u043d\u0442\u044b", "\u0412\u043e\u043b\u043a\u0438"};
        ChatColor[] colors = {ChatColor.RED, ChatColor.AQUA, ChatColor.GOLD, ChatColor.LIGHT_PURPLE, ChatColor.DARK_PURPLE, ChatColor.GREEN};
        String[] materials = {"RED_BANNER", "LIGHT_BLUE_BANNER", "GOLD_BANNER", "MAGENTA_BANNER", "PURPLE_BANNER", "GREEN_BANNER"};

        for (int i = 0; i < nations.length; i++) {
            player.sendMessage(colors[i] + " \u25b6 " + nations[i] + ChatColor.GRAY + " (" + ChatColor.WHITE + materials[i] + ChatColor.GRAY + ")");
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + " \u0411\u043e\u043b\u0435\u0435 \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439: /nation");
        player.sendMessage("");
    }

    private void showRecentLogins(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&e&l[\u041f\u041e\u0421\u041b\u0415\u0414\u041d\u0418\u0415 \u0417\u0410\u0425\u041e\u0414\u042b]"));
        player.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        List<Player> players = new ArrayList<>(online);
        int count = 0;
        for (int i = players.size() - 1; i >= 0 && count < 5; i--, count++) {
            Player p = players.get(i);
            long joinTime = plugin.getAuthManager().getJoinTime(p);
            String timeAgo = joinTime > 0 ? formatTimeDiff(System.currentTimeMillis() - joinTime) + " \u043d\u0430\u0437\u0430\u0434" : "\u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e";
            player.sendMessage(ChatColor.YELLOW + " " + (count + 1) + ". " + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " \u2014 " + timeAgo);
        }

        if (players.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + " \u041d\u0438\u043a\u043e\u0433\u043e \u043d\u0435\u0442 \u043e\u043d\u043b\u0430\u0439\u043d.");
        }
        player.sendMessage("");
    }

    private void showWarningsBans(Player player) {
        int totalWarns = 0;
        int totalBans = 0;
        try {
            org.bukkit.BanList banList = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
            for (org.bukkit.BanEntry entry : banList.getBanEntries()) {
                totalBans++;
            }
        } catch (Exception ignored) {}

        try {
            java.io.File warnsFile = new java.io.File(plugin.getDataFolder(), "warns.yml");
            if (warnsFile.exists()) {
                org.bukkit.configuration.file.YamlConfiguration cfg =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(warnsFile);
                org.bukkit.configuration.ConfigurationSection players = cfg.getConfigurationSection("players");
                if (players != null) {
                    for (String key : players.getKeys(false)) {
                        totalWarns += players.getInt(key + ".count", 0);
                    }
                }
            }
        } catch (Exception ignored) {}

        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&l[\u041c\u041e\u0414\u0415\u0420\u0410\u0426\u0418\u042f]"));
        player.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        player.sendMessage(ChatColor.GREEN + " \u25b6 \u0410\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u0432\u0430\u0440\u043d\u043e\u0432: " + ChatColor.YELLOW + totalWarns);
        player.sendMessage(ChatColor.GREEN + " \u25b6 \u0410\u043a\u0442\u0438\u0432\u043d\u044b\u0445 \u0431\u0430\u043d\u043e\u0432: " + ChatColor.YELLOW + totalBans);
        player.sendMessage(ChatColor.GRAY + " \u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435: /warn, /unwarn, /warns, /clearwarns");
        player.sendMessage("");
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTION ITEMS
    // ═══════════════════════════════════════════════════════════════

    private void startBroadcast(Player player) {
        awaitingBroadcast.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&4&l[\u0421\u041e\u041e\u0411\u0429\u0415\u041d\u0418\u0415 \u0420\u041e\u0421\u0421\u041b\u0410\u041d\u041e]"));
        player.sendMessage(ChatColor.GRAY + "\u041d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435 \u0432 \u0447\u0430\u0442 (\u043f\u043e\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044f &\u0446\u0432\u0435\u0442\u0430):");
        player.sendMessage(ChatColor.RED + " \u2014 \u0414\u043b\u044f \u043e\u0442\u043c\u0435\u043d\u044b \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u043b\u044e\u0431\u043e\u0435 \u0441\u043b\u043e\u0432\u043e \u0438\u043b\u0438 /cancel");
        player.sendMessage("");
    }

    private void doReload(Player player) {
        plugin.reloadAll();
        player.sendMessage(ChatColor.GREEN + "\u2713 VKChat \u043a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044f \u043f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u0430!");
        player.closeInventory();
    }

    private void toggleMaintenance(Player player) {
        String path = "server.maintenance";
        boolean current = plugin.getConfig().getBoolean(path, false);
        plugin.getConfig().set(path, !current);
        plugin.saveConfig();
        plugin.reloadConfig();

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                (!current ? "&a&l\u2713 \u0420\u0435\u0436\u0438\u043c \u043e\u0431\u0441\u043b\u0443\u0436\u0438\u0432\u0430\u043d\u0438\u044f \u0412\u041a\u041b\u042e\u0427\u0415\u041d" :
                        "&c&l\u2717 \u0420\u0435\u0436\u0438\u043c \u043e\u0431\u0441\u043b\u0443\u0436\u0438\u0432\u0430\u043d\u0438\u044f \u0412\u042b\u041a\u041b\u042e\u0427\u0415\u041d")));
        player.closeInventory();
    }

    private void triggerBackup(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&e&l\u23f1 \u0417\u0430\u043f\u0443\u0441\u043a \u0431\u044d\u043a\u0430\u043f\u0430 \u043c\u0438\u0440\u0430..."));
        player.closeInventory();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                org.bukkit.World world = Bukkit.getWorlds().get(0);
                if (world != null) {
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date());
                    String folderName = "backup_" + timestamp;
                    player.sendMessage(ChatColor.GREEN + "\u2713 \u0411\u044d\u043a\u0430\u043f \u043c\u0438\u0440\u0430 \"" + world.getName() + "\" \u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d \u043a\u0430\u043a " + folderName);
                    player.sendMessage(ChatColor.GRAY + " \u041f\u0443\u0442\u044c: plugins/VKChat/" + folderName);
                }
            } catch (Exception ex) {
                player.sendMessage(ChatColor.RED + "\u2717 \u041e\u0448\u0438\u0431\u043a\u0430 \u0431\u044d\u043a\u0430\u043f\u0430: " + ex.getMessage());
            }
        });
    }

    private void startPlayerSearch(Player player) {
        awaitingPlayerSearch.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&b&l[\u041f\u041e\u0418\u0421\u041a \u0418\u0413\u0420\u041e\u041a\u0410]"));
        player.sendMessage(ChatColor.GRAY + "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0438\u043c\u044f \u0438\u0433\u0440\u043e\u043a\u0430 \u0432 \u0447\u0430\u0442\u0435:");
        player.sendMessage(ChatColor.RED + " \u2014 \u0414\u043b\u044f \u043e\u0442\u043c\u0435\u043d\u044b \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u043b\u044e\u0431\u043e\u0435 \u0441\u043b\u043e\u0432\u043e \u0438\u043b\u0438 /cancel");
        player.sendMessage("");
    }

    private void showPlayerInfo(Player player, String targetName) {
        org.bukkit.OfflinePlayer offline = ru.example.vkchat.util.UUIDResolver.resolve(targetName);
        if (offline == null) {
            player.sendMessage(ChatColor.RED + "\u2717 \u0418\u0433\u0440\u043e\u043a \"" + targetName + "\" \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d.");
            return;
        }

        UUID uuid = offline.getUniqueId();
        Player online = Bukkit.getPlayerExact(targetName);

        int vkId = plugin.getAuthManager().getLinkedVkId(offline.getUniqueId());
        int rep = vkId != -1 ? plugin.getReputationManager().getPoints(vkId) : -1;
        int warns = plugin.getWarnManager().getWarns(targetName);
        boolean isOnline = online != null && online.isOnline();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int kills = plugin.getStatsManager().getKills(uuid);
            int deaths = plugin.getStatsManager().getDeaths(uuid);
            int blocks = plugin.getStatsManager().getBlocks(uuid);
            int achievements = plugin.getStatsManager().getAchievements(uuid);

            String nation = "\u041d\u0435\u0442 \u0434\u0430\u043d\u043d\u044b\u0445";
            String nationPath = "nations." + targetName.toLowerCase() + ".nation";
            String configured = plugin.getConfig().getString(nationPath);
            if (configured != null) {
                nation = configured;
            }
            final String finalNation = nation;

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&b&l[\u0418\u041d\u0424\u041e \u041e\u0411 \u0418\u0413\u0420\u041e\u041a\u0415: " + targetName + "]"));
                player.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0421\u0442\u0430\u0442\u0443\u0441: " + (isOnline ? ChatColor.GREEN + "\u041e\u043d\u043b\u0430\u0439\u043d" : ChatColor.RED + "\u041e\u0444\u0444\u043b\u0430\u0439\u043d"));
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0420\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f VK: " + (rep >= 0 ? ChatColor.YELLOW + String.valueOf(rep) : ChatColor.RED + "\u041d\u0435 \u043f\u0440\u0438\u0432\u044f\u0437\u0430\u043d"));
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u041d\u0430\u0446\u0438\u044f: " + ChatColor.AQUA + finalNation);
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0412\u0430\u0440\u043d\u044b: " + (warns > 0 ? ChatColor.RED + String.valueOf(warns) + "/3" : ChatColor.GREEN + "0"));
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0423\u0431\u0438\u0439\u0441\u0442\u0432\u0430: " + ChatColor.YELLOW + kills);
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0421\u043c\u0435\u0440\u0442\u0435\u0439: " + ChatColor.YELLOW + deaths);
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0411\u043b\u043e\u043a\u043e\u0432: " + ChatColor.YELLOW + blocks);
                player.sendMessage(ChatColor.GREEN + " \u25b6 \u0414\u043e\u0441\u0442\u0438\u0436\u0435\u043d\u0438\u0439: " + ChatColor.YELLOW + achievements);
                player.sendMessage("");
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // GUI ITEMS
    // ═══════════════════════════════════════════════════════════════

    private ItemStack serverStatsItem() {
        int online = Bukkit.getOnlinePlayers().size();
        double tps = getTps();
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        return item(Material.BEACON,
                ChatColor.translateAlternateColorCodes('&', "&4&l\u2605 \u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430"),
                ChatColor.GRAY + "\u0418\u043d\u0444\u043e\u0440\u043c\u0430\u0446\u0438\u044f \u043e \u0441\u0435\u0440\u0432\u0435\u0440\u0435",
                "",
                ChatColor.GREEN + " \u25b6 \u041e\u043d\u043b\u0430\u0439\u043d: " + ChatColor.YELLOW + online + "/" + Bukkit.getMaxPlayers(),
                ChatColor.GREEN + " \u25b6 TPS: " + ChatColor.YELLOW + String.format("%.1f", tps),
                ChatColor.GREEN + " \u25b6 \u0410\u043f\u0442\u0430\u0439\u043c: " + ChatColor.YELLOW + formatUptime(uptime),
                "",
                ChatColor.DARK_GRAY + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439"
        );
    }

    private ItemStack economyItem() {
        return item(Material.EMERALD,
                ChatColor.translateAlternateColorCodes('&', "&a&l\uD83D\uDCB0 \u042d\u043a\u043e\u043d\u043e\u043c\u0438\u043a\u0430"),
                ChatColor.GRAY + "\u041e\u0431\u0437\u043e\u0440 \u044d\u043a\u043e\u043d\u043e\u043c\u0438\u043a\u0438 \u0441\u0435\u0440\u0432\u0435\u0440\u0430",
                "",
                ChatColor.YELLOW + " \u0422\u043e\u043f \u0440\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u0438,",
                ChatColor.YELLOW + " \u0431\u0430\u043b\u043b\u0430\u043d\u0441,",
                ChatColor.YELLOW + " \u043e\u0431\u043e\u0440\u043e\u0442.",
                "",
                ChatColor.DARK_GRAY + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439"
        );
    }

    private ItemStack nationOverviewItem() {
        return item(Material.SHIELD,
                ChatColor.translateAlternateColorCodes('&', "&b&l\uD83C\uDFDB \u041d\u0430\u0446\u0438\u0438"),
                ChatColor.GRAY + "\u041e\u0431\u0437\u043e\u0440 \u043d\u0430\u0446\u0438\u0439",
                "",
                ChatColor.AQUA + " \u0425\u0440\u0430\u043d\u0442\u044b, \u0421\u043f\u0438\u0440\u0438\u0442\u044b,",
                ChatColor.AQUA + " \u041c\u0435\u0445\u0430\u043d\u0438\u043a\u0438, \u041c\u0430\u0433\u0438,",
                ChatColor.AQUA + " \u041d\u0435\u043a\u0440\u043e\u043c\u0430\u043d\u0442\u044b, \u0412\u043e\u043b\u043a\u0438.",
                "",
                ChatColor.DARK_GRAY + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439"
        );
    }

    private ItemStack recentLoginsItem() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        return item(Material.PLAYER_HEAD,
                ChatColor.translateAlternateColorCodes('&', "&e&l\uD83D\uDD25 \u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0435 \u0437\u0430\u0445\u043e\u0434\u044b"),
                ChatColor.GRAY + "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0435 5 \u0437\u0430\u0445\u043e\u0434\u043e\u0432",
                "",
                ChatColor.YELLOW + " \u0421\u0435\u0439\u0447\u0430\u0441 \u043e\u043d\u043b\u0430\u0439\u043d: " + ChatColor.WHITE + online.size(),
                "",
                ChatColor.DARK_GRAY + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439"
        );
    }

    private ItemStack warningsBansItem() {
        return item(Material.PAPER,
                ChatColor.translateAlternateColorCodes('&', "&c&l\uD83D\uDEA8 \u041c\u043e\u0434\u0435\u0440\u0430\u0446\u0438\u044f"),
                ChatColor.GRAY + "\u0412\u0430\u0440\u043d\u044b \u0438 \u0431\u0430\u043d\u044b",
                "",
                ChatColor.RED + " \u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435: /warn",
                ChatColor.RED + " \u0421\u043d\u044f\u0442\u0438\u0435: /unwarn",
                ChatColor.RED + " \u041f\u0440\u043e\u0441\u043c\u043e\u0442\u0440: /warns",
                "",
                ChatColor.DARK_GRAY + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439"
        );
    }

    private ItemStack broadcastItem() {
        return item(Material.BELL,
                ChatColor.translateAlternateColorCodes('&', "&6&l\uD83D\uDCE2 \u041e\u0431\u044a\u044f\u0432\u043b\u0435\u043d\u0438\u0435"),
                ChatColor.GRAY + "\u041e\u0442\u043f\u0440\u0430\u0432\u0438\u0442\u044c \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0435",
                ChatColor.GRAY + "\u0432\u0441\u0435\u043c \u0438\u0433\u0440\u043e\u043a\u0430\u043c \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435",
                "",
                ChatColor.YELLOW + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0438 \u043d\u0430\u043f\u0438\u0448\u0438\u0442\u0435 \u0432 \u0447\u0430\u0442"
        );
    }

    private ItemStack reloadItem() {
        return item(Material.REDSTONE,
                ChatColor.translateAlternateColorCodes('&', "&a&l\uD83D\uDD04 \u041f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0430"),
                ChatColor.GRAY + "\u041f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0432\u0441\u0435 \u043a\u043e\u043d\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u0438",
                "",
                ChatColor.YELLOW + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u0435\u0440\u0435\u0437\u0430\u0433\u0440\u0443\u0437\u043a\u0438"
        );
    }

    private ItemStack maintenanceItem() {
        boolean maintenance = plugin.getConfig().getBoolean("server.maintenance", false);
        return item(Material.LEVER,
                ChatColor.translateAlternateColorCodes('&',
                        (maintenance ? "&c&l\uD83D\uDD34 \u0420\u0435\u0436\u0438\u043c \u041e\u0431\u0441\u043b\u0443\u0436\u0438\u0432\u0430\u043d\u0438\u044f" :
                                "&a&l\uD83D\uDFE2 \u0420\u0435\u0436\u0438\u043c \u041e\u0431\u0441\u043b\u0443\u0436\u0438\u0432\u0430\u043d\u0438\u044f")),
                ChatColor.GRAY + (maintenance ? "\u0412\u043a\u043b\u044e\u0447\u0451\u043d" : "\u0412\u044b\u043a\u043b\u044e\u0447\u0451\u043d"),
                "",
                ChatColor.YELLOW + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u044f"
        );
    }

    private ItemStack backupItem() {
        return item(Material.CHEST,
                ChatColor.translateAlternateColorCodes('&', "&e&l\uD83D\uDCC1 \u0411\u044d\u043a\u0430\u043f"),
                ChatColor.GRAY + "\u0417\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u044c \u0431\u044d\u043a\u0430\u043f \u043c\u0438\u0440\u0430",
                "",
                ChatColor.YELLOW + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u0431\u044d\u043a\u0430\u043f\u0430"
        );
    }

    private ItemStack playerSearchItem() {
        return item(Material.COMPASS,
                ChatColor.translateAlternateColorCodes('&', "&b&l\uD83D\uDD0D \u041f\u043e\u0438\u0441\u043a \u0438\u0433\u0440\u043e\u043a\u0430"),
                ChatColor.GRAY + "\u041f\u043e\u0438\u0441\u043a \u043f\u043e \u0438\u043c\u0435\u043d\u0438",
                ChatColor.GRAY + "\u0440\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f, \u043d\u0430\u0446\u0438\u044f,",
                ChatColor.GRAY + "\u0438\u0433\u0440\u043e\u0432\u043e\u0435 \u0432\u0440\u0435\u043c\u044f,",
                ChatColor.GRAY + "\u0432\u0430\u0440\u043d\u044b.",
                "",
                ChatColor.YELLOW + "\u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0438 \u0432\u0432\u0435\u0434\u0438\u0442\u0435 \u0438\u043c\u044f"
        );
    }

    private ItemStack closeItem() {
        return item(Material.BARRIER,
                ChatColor.translateAlternateColorCodes('&', "&c\u2715 \u0417\u0430\u043a\u0440\u044b\u0442\u044c"),
                ChatColor.GRAY + "\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043f\u0430\u043d\u0435\u043b\u044c"
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private ItemStack glass(Material mat) {
        return item(mat, " ");
    }

    private ItemStack headerItem(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "\u2500\u2500\u2500 \u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0441\u0435\u0440\u0432\u0435\u0440\u043e\u043c \u2500\u2500\u2500"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        is.setItemMeta(meta);
        return is;
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        is.setItemMeta(meta);
        return is;
    }

    private String formatUptime(long millis) {
        long sec = millis / 1000;
        long min = sec / 60;
        long hr = min / 60;
        long day = hr / 24;
        if (day > 0) return day + "d " + (hr % 24) + "h " + (min % 60) + "m";
        if (hr > 0) return hr + "h " + (min % 60) + "m " + (sec % 60) + "s";
        if (min > 0) return min + "m " + (sec % 60) + "s";
        return sec + "s";
    }

    private String formatTimeDiff(long ms) {
        long sec = ms / 1000;
        if (sec < 60) return sec + " \u0441\u0435\u043a";
        long min = sec / 60;
        if (min < 60) return min + " \u043c\u0438\u043d";
        long hr = min / 60;
        return hr + " \u0447 " + (min % 60) + " \u043c\u0438\u043d";
    }

    private double getTps() {
        try {
            org.bukkit.Server server = Bukkit.getServer();
            java.lang.reflect.Method method = server.getClass().getMethod("getTPS");
            double[] tps = (double[]) method.invoke(server);
            return tps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER HOLDER
    // ═══════════════════════════════════════════════════════════════

    private static class AdminHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
