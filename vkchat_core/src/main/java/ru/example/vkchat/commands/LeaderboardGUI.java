package ru.example.vkchat.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.example.vkchat.VKChatPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardGUI implements CommandExecutor, Listener {

    private static final String TITLE = ChatColor.translateAlternateColorCodes('&',
            "\u00a78\u25b8 \u00a76\u00a7l\u0420\u0415\u0419\u0422\u0418\u041d\u0413 \u00a78\u25c4 \u00a77\u041b\u0443\u0447\u0448\u0438\u0435 \u0438\u0433\u0440\u043e\u043a\u0438");
    private static final int SIZE = 54;
    private static final long CACHE_TTL_MS = 60_000;

    private static final String PAGE_REPUTATION = "reputation";
    private static final String PAGE_KILLERS = "killers";
    private static final String PAGE_MINERS = "miners";

    private final VKChatPlugin plugin;
    private final Map<String, CachedPage> cache = new ConcurrentHashMap<>();

    public LeaderboardGUI(VKChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "\u042d\u0442\u0443 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 \u043c\u043e\u0436\u0435\u0442 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u044c \u0442\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a.");
            return true;
        }
        Player player = (Player) sender;

        int page = 0;
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            switch (arg) {
                case "rep":
                case "\u0440\u0435\u043f":
                case "reputation":
                case "\u0440\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f":
                    page = 0; break;
                case "kills":
                case "\u0443\u0431\u0438\u0439\u0441\u0442\u0432\u0430":
                case "killers":
                case "\u043a\u0438\u043b\u043b\u0435\u0440\u044b":
                    page = 1; break;
                case "miners":
                case "\u0448\u0430\u0445\u0442\u0451\u0440\u044b":
                case "blocks":
                case "\u0431\u043b\u043e\u043a\u0438":
                    page = 2; break;
                default:
                    try {
                        page = Integer.parseInt(arg) - 1;
                        page = Math.max(0, Math.min(2, page));
                    } catch (NumberFormatException ignored) {}
            }
        }

        openLeaderboard(player, page);
        return true;
    }

    public void openLeaderboard(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new LeaderboardHolder(page), SIZE, TITLE);

        ItemStack border = glass(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, border);
        }

        String pageType = getPageType(page);
        List<LeaderboardEntry> entries = getEntries(pageType);

        String pageName;
        switch (page) {
            case 0: pageName = "\u0420\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f"; break;
            case 1: pageName = "\u0423\u0431\u0438\u0439\u0441\u0442\u0432\u0430"; break;
            case 2: pageName = "\u0411\u043b\u043e\u043a\u0438"; break;
            default: pageName = ""; break;
        }

        inv.setItem(4, headerItem("\u00a76\u00a7l\u0421\u0442\u0440\u0430\u043d\u0438\u0446\u0430 " + (page + 1) + "/3 \u2014 " + pageName));

        int slots[] = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        int display = Math.min(entries.size(), slots.length);
        for (int i = 0; i < display; i++) {
            LeaderboardEntry entry = entries.get(i);
            inv.setItem(slots[i], createPlayerItem(entry, pageType, i + 1));
        }

        if (page > 0) {
            inv.setItem(45, navItem(Material.ARROW, "\u00a77\u041f\u0440\u0435\u0434\u044b\u0434\u0443\u0449\u0430\u044f \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0438\u044f", "\u00a78\u0421\u0442\u0440. " + page));
        }
        if (page < 2) {
            inv.setItem(53, navItem(Material.ARROW, "\u00a77\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0430\u044f \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0438\u044f", "\u00a78\u0421\u0442\u0440. " + (page + 2)));
        }

        inv.setItem(49, navItem(Material.BARRIER, "\u00a7c\u2715 \u0417\u0430\u043a\u0440\u044b\u0442\u044c", ""));

        player.openInventory(inv);
    }

    private void showPlayerStats(Player viewer, LeaderboardEntry entry, String pageType) {
        viewer.closeInventory();
        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&6&l[\u041f\u0420\u041e\u0424\u0418\u041b\u042c \u0418\u0413\u0420\u041e\u041a\u0410: " + entry.name + "]"));
        viewer.sendMessage(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        viewer.sendMessage(ChatColor.GREEN + " \u25b6 \u041c\u0435\u0441\u0442\u043e \u0432 \u0440\u0435\u0439\u0442\u0438\u043d\u0433\u0435: " + ChatColor.YELLOW + "#" + entry.rank);

        String scoreLabel;
        switch (pageType) {
            case PAGE_REPUTATION: scoreLabel = "\u0420\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f VK"; break;
            case PAGE_KILLERS: scoreLabel = "\u0423\u0431\u0438\u0439\u0441\u0442\u0432"; break;
            case PAGE_MINERS: scoreLabel = "\u0411\u043b\u043e\u043a\u043e\u0432"; break;
            default: scoreLabel = "\u041e\u0447\u043a\u0438"; break;
        }
        viewer.sendMessage(ChatColor.GREEN + " \u25b6 " + scoreLabel + ": " + ChatColor.YELLOW + entry.score);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = entry.uuid;
            int kills = plugin.getStatsManager().getKills(uuid);
            int deaths = plugin.getStatsManager().getDeaths(uuid);
            int blocks = plugin.getStatsManager().getBlocks(uuid);
            int achievements = plugin.getStatsManager().getAchievements(uuid);
            int vkId = plugin.getAuthManager().getLinkedVkId(uuid);
            int rep = vkId != -1 ? plugin.getReputationManager().getPoints(vkId) : 0;
            double kd = deaths > 0 ? (double) kills / deaths : kills;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                viewer.sendMessage(ChatColor.GREEN + " \u25b6 \u0423\u0431\u0438\u0439\u0441\u0442\u0432: " + ChatColor.YELLOW + kills);
                viewer.sendMessage(ChatColor.GREEN + " \u25b6 \u0421\u043c\u0435\u0440\u0442\u0435\u0439: " + ChatColor.YELLOW + deaths);
                viewer.sendMessage(ChatColor.GREEN + " \u25b6 K/D: " + ChatColor.YELLOW + String.format("%.2f", kd));
                viewer.sendMessage(ChatColor.GREEN + " \u25b6 \u0411\u043b\u043e\u043a\u043e\u0432: " + ChatColor.YELLOW + blocks);
                viewer.sendMessage(ChatColor.GREEN + " \u25b6 \u0414\u043e\u0441\u0442\u0438\u0436\u0435\u043d\u0438\u0439: " + ChatColor.YELLOW + achievements);
                viewer.sendMessage(ChatColor.GREEN + " \u25b6 \u0420\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f VK: " + ChatColor.YELLOW + rep);
                viewer.sendMessage("");
            });
        });
    }

    private List<LeaderboardEntry> getEntries(String pageType) {
        CachedPage cached = cache.get(pageType);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.entries;
        }

        List<LeaderboardEntry> entries = loadEntries(pageType);
        cache.put(pageType, new CachedPage(entries, System.currentTimeMillis()));
        return entries;
    }

    private List<LeaderboardEntry> loadEntries(String pageType) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            switch (pageType) {
                case PAGE_REPUTATION:
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT r.vk_id, r.points FROM vkchat_reputation r ORDER BY r.points DESC LIMIT 28")) {
                        ResultSet rs = ps.executeQuery();
                        int rank = 0;
                        while (rs.next()) {
                            rank++;
                            int vkId = rs.getInt("vk_id");
                            int points = rs.getInt("points");
                            String name = resolveVkName(vkId);
                            UUID uuid = resolveUuidByVkId(conn, vkId);
                            entries.add(new LeaderboardEntry(rank, name, points, uuid, vkId));
                        }
                    }
                    break;

                case PAGE_KILLERS:
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT s.uuid, s.kills FROM vkchat_stats s WHERE s.kills > 0 ORDER BY s.kills DESC LIMIT 28")) {
                        ResultSet rs = ps.executeQuery();
                        int rank = 0;
                        while (rs.next()) {
                            rank++;
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            int kills = rs.getInt("kills");
                            String name = resolvePlayerName(uuid);
                            entries.add(new LeaderboardEntry(rank, name, kills, uuid, -1));
                        }
                    }
                    break;

                case PAGE_MINERS:
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT s.uuid, s.blocks FROM vkchat_stats s WHERE s.blocks > 0 ORDER BY s.blocks DESC LIMIT 28")) {
                        ResultSet rs = ps.executeQuery();
                        int rank = 0;
                        while (rs.next()) {
                            rank++;
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            int blocks = rs.getInt("blocks");
                            String name = resolvePlayerName(uuid);
                            entries.add(new LeaderboardEntry(rank, name, blocks, uuid, -1));
                        }
                    }
                    break;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Leaderboard DB error: " + e.getMessage());
        }
        return entries;
    }

    private String resolveVkName(int vkId) {
        try {
            org.json.JSONObject user = plugin.getVkManager().getUserInfo(vkId);
            if (user != null) {
                return user.getString("first_name") + " " + user.getString("last_name");
            }
        } catch (Exception ignored) {}
        return "VK#" + vkId;
    }

    private String resolvePlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : "Unknown";
    }

    private UUID resolveUuidByVkId(Connection conn, int vkId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM vkchat_auth WHERE vk_id = ?")) {
            ps.setInt(1, vkId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        }
        return null;
    }

    private ItemStack createPlayerItem(LeaderboardEntry entry, String pageType, int position) {
        Material mat;
        switch (position) {
            case 1: mat = Material.GOLD_BLOCK; break;
            case 2: mat = Material.GOLD_INGOT; break;
            case 3: mat = Material.GOLD_NUGGET; break;
            default: mat = Material.PLAYER_HEAD; break;
        }

        ItemStack skull = new ItemStack(mat);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (entry.uuid != null && mat == Material.PLAYER_HEAD) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid);
            meta.setOwningPlayer(op);
        }

        String rankPrefix;
        switch (position) {
            case 1: rankPrefix = "\u00a76\u00a7l\u2605 #1 \u2605 "; break;
            case 2: rankPrefix = "\u00a7f\u00a7l#2 "; break;
            case 3: rankPrefix = "\u00a76\u00a7l#3 "; break;
            default: rankPrefix = "\u00a77#" + position + " "; break;
        }

        String scoreLabel;
        switch (pageType) {
            case PAGE_REPUTATION: scoreLabel = "\u0440\u0435\u043f."; break;
            case PAGE_KILLERS: scoreLabel = "\u043a\u0438\u043b\u043b\u043e\u0432"; break;
            case PAGE_MINERS: scoreLabel = "\u0431\u043b\u043e\u043a\u043e\u0432"; break;
            default: scoreLabel = "\u043e\u0447\u043a\u043e\u0432"; break;
        }

        meta.setDisplayName(rankPrefix + entry.name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        lore.add(ChatColor.GREEN + " \u25b6 \u0421\u043a\u043e\u0440: " + ChatColor.YELLOW + entry.score + " " + scoreLabel);

        if (entry.uuid != null) {
            int kills = plugin.getStatsManager().getKills(entry.uuid);
            int blocks = plugin.getStatsManager().getBlocks(entry.uuid);
            lore.add(ChatColor.GREEN + " \u25b6 \u0423\u0431\u0438\u0439\u0441\u0442\u0432: " + ChatColor.WHITE + kills);
            lore.add(ChatColor.GREEN + " \u25b6 \u0411\u043b\u043e\u043a\u043e\u0432: " + ChatColor.WHITE + blocks);
        }

        if (position <= 3) {
            lore.add("");
            lore.add(ChatColor.GOLD + "\u271e \u0422\u043e\u043f-3 \u0438\u0433\u0440\u043e\u043a!");
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + " \u041d\u0430\u0436\u043c\u0438\u0442\u0435 \u0434\u043b\u044f \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u0435\u0439");

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        skull.setItemMeta(meta);
        return skull;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (!(e.getInventory().getHolder() instanceof LeaderboardHolder)) return;
        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        LeaderboardHolder holder = (LeaderboardHolder) e.getInventory().getHolder();
        int currentPage = holder.getPage();

        if (slot == 45 && currentPage > 0) {
            openLeaderboard(player, currentPage - 1);
        } else if (slot == 53 && currentPage < 2) {
            openLeaderboard(player, currentPage + 1);
        } else if (slot == 49) {
            player.closeInventory();
        } else if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.PLAYER_HEAD
                || (e.getCurrentItem() != null && (e.getCurrentItem().getType() == Material.GOLD_BLOCK
                || e.getCurrentItem().getType() == Material.GOLD_INGOT
                || e.getCurrentItem().getType() == Material.GOLD_NUGGET))) {
            String pageType = getPageType(currentPage);
            List<LeaderboardEntry> entries = getEntries(pageType);
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot && i < entries.size()) {
                    showPlayerStats(player, entries.get(i), pageType);
                    return;
                }
            }
        }
    }

    private String getPageType(int page) {
        switch (page) {
            case 0: return PAGE_REPUTATION;
            case 1: return PAGE_KILLERS;
            case 2: return PAGE_MINERS;
            default: return PAGE_REPUTATION;
        }
    }

    private ItemStack glass(Material mat) {
        return item(mat, " ");
    }

    private ItemStack headerItem(String name) {
        ItemStack is = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "\u0421\u0442\u0440. 1 \u2014 \u0420\u0435\u043f\u0443\u0442\u0430\u0446\u0438\u044f");
        lore.add(ChatColor.GRAY + "\u0421\u0442\u0440. 2 \u2014 \u0423\u0431\u0438\u0439\u0441\u0442\u0432\u0430");
        lore.add(ChatColor.GRAY + "\u0421\u0442\u0440. 3 \u2014 \u0411\u043b\u043e\u043a\u0438");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        is.setItemMeta(meta);
        return is;
    }

    private ItemStack navItem(Material mat, String name, String desc) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        meta.setDisplayName(name);
        if (!desc.isEmpty()) {
            meta.setLore(Collections.singletonList(ChatColor.GRAY + desc));
        }
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

    // === Inner classes ===

    private static class LeaderboardHolder implements InventoryHolder {
        private final int page;

        LeaderboardHolder(int page) {
            this.page = page;
        }

        int getPage() { return page; }

        @Override
        public Inventory getInventory() { return null; }
    }

    private static class LeaderboardEntry {
        final int rank;
        final String name;
        final int score;
        final UUID uuid;
        final int vkId;

        LeaderboardEntry(int rank, String name, int score, UUID uuid, int vkId) {
            this.rank = rank;
            this.name = name;
            this.score = score;
            this.uuid = uuid;
            this.vkId = vkId;
        }
    }

    private static class CachedPage {
        final List<LeaderboardEntry> entries;
        final long timestamp;

        CachedPage(List<LeaderboardEntry> entries, long timestamp) {
            this.entries = entries;
            this.timestamp = timestamp;
        }
    }
}
