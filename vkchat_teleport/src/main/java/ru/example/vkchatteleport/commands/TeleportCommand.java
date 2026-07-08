package ru.example.vkchatteleport.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.example.vkchatteleport.VKChatTeleportPlugin;
import ru.example.vkchatteleport.manager.TeleportManager;
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeleportCommand implements CommandExecutor, TabCompleter {
    private final VKChatTeleportPlugin plugin;

    public TeleportCommand(VKChatTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду могут выполнять только игроки!");
            return true;
        }
        
        Player p = (Player) sender;
        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
        if (vkId == -1 && !ru.example.vkchat.util.VKChatBridge.hasPass(p)) {
            p.sendMessage(ChatColor.RED + "❌ Для использования телепортации необходимо привязать ВКонтакте! Напишите: " + ChatColor.YELLOW + "/vklink");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "rtp":
                handleRtp(p, vkId);
                return true;
            case "sethome":
                handleSetHome(p, args);
                return true;
            case "home":
                handleHome(p, vkId, args);
                return true;
            case "delhome":
                handleDelHome(p, args);
                return true;
            case "homes":
                handleHomesList(p);
                return true;
            case "gateway":
            case "portal":
                handleGateway(p, vkId, args);
                return true;
            case "tpa":
                if (args.length >= 1 && args[0].equalsIgnoreCase("cancel")) {
                    handleTpaCancel(p);
                } else {
                    handleTpa(p, vkId, args);
                }
                return true;
            case "tpahere":
                handleTpaHere(p, vkId, args);
                return true;
            case "tpaccept":
                handleTpAccept(p);
                return true;
            case "tpdeny":
            case "tpdecline":
                handleTpDeny(p);
                return true;
            case "back":
                handleBack(p, vkId);
                return true;
            case "tphistory":
                handleTpHistory(p);
                return true;
        }

        return false;
    }

    // ==========================================
    // МЕЖФРАКЦИОННЫЕ ПОРТАЛЫ (/gateway <soviet|pagan|imperial>)
    // ==========================================
    private void handleGateway(Player p, int vkId, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ChatColor.RED + "❌ Использование: " + ChatColor.YELLOW + "/gateway <soviet|pagan|imperial>");
            p.sendMessage(ChatColor.GRAY + "Телепортация на базу выбранной фракции стоит " + ChatColor.GOLD + "25 реп. ВК" + ChatColor.GRAY + ".");
            return;
        }

        String targetNation = args[0].toLowerCase();
        Location targetLoc = null;
        String nationName = "";

        if (targetNation.contains("soviet") || targetNation.contains("совет")) {
            targetLoc = new Location(p.getWorld(), 100.5, 80.0, 100.5);
            nationName = "Советский Союз";
        } else if (targetNation.contains("pagan") || targetNation.contains("языч")) {
            targetLoc = new Location(p.getWorld(), -500.5, 80.0, -500.5);
            nationName = "Языческий Культ";
        } else if (targetNation.contains("imperial") || targetNation.contains("импер")) {
            targetLoc = new Location(p.getWorld(), 1000.5, 80.0, -1000.5);
            nationName = "Священная Империя";
        } else {
            p.sendMessage(ChatColor.RED + "❌ Неизвестная фракция! Доступные: soviet, pagan, imperial");
            return;
        }

        // Ставим игрока на безопасный Y над землей
        targetLoc.setY(p.getWorld().getHighestBlockYAt(targetLoc) + 1.0);

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int cost = applyDonateDiscount(p, 25);

        if (currentRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации! Для перемещения через портал требуется: " + ChatColor.GOLD + cost + ChatColor.RED + " реп. ВК.");
            return;
        }

        String finalNationName = nationName;
        plugin.getTeleportManager().startTeleportWarmup(
                p,
                targetLoc,
                ChatColor.GREEN + "✨ Врата открылись! Вы телепортировались в столицу: " + ChatColor.YELLOW + finalNationName + ChatColor.GREEN + " за " + ChatColor.GOLD + cost + ChatColor.GREEN + " реп. ВК!",
                cost,
                null,
                null
        );
    }

    // ==========================================
    // СЛУЧАЙНЫЙ ТЕЛЕПОРТ (/rtp)
    // ==========================================
    private void handleRtp(Player p, int vkId) {
        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int cost = plugin.getConfig().getInt("teleportation.rtp.cost", 250);
        cost = Math.max(1, applyDonateDiscount(p, cost));

        int cooldown = getDonateCooldown(p, "rtp");

        long cdRemaining = plugin.getTeleportManager().getCooldownRemaining("rtp", p.getUniqueId(), cooldown);
        if (cdRemaining > 0) {
            p.sendMessage(ChatColor.RED + "⏳ Подождите " + ChatColor.GOLD + formatTime(cdRemaining));
            return;
        }

        if (currentRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Нужно " + ChatColor.GOLD + cost + ChatColor.RED + " реп.");
            return;
        }

        p.sendMessage(ChatColor.YELLOW + "🔍 Поиск безопасной точки...");

        Location safeLoc = findSafeLocation(p);
        if (safeLoc == null) {
            p.sendMessage(ChatColor.RED + "❌ Не удалось найти безопасное место!");
            return;
        }

        plugin.getTeleportManager().startTeleportWarmup(
                p,
                safeLoc,
                ChatColor.GREEN + "✨ Телепортация за " + ChatColor.GOLD + cost + ChatColor.GREEN + " реп!",
                cost,
                "rtp",
                null
        );
    }


    private int applyDonateDiscount(Player p, int cost) {
        if (cost <= 0) return 0;
        if (p.hasPermission("vkchat.donate.overlord")) return Math.max(1, (int)(cost * 0.35));
        if (p.hasPermission("vkchat.donate.legend")) return Math.max(1, (int)(cost * 0.50));
        if (p.hasPermission("vkchat.donate.star")) return Math.max(1, (int)(cost * 0.65));
        if (p.hasPermission("vkchat.donate.flame")) return Math.max(1, (int)(cost * 0.80));
        if (p.hasPermission("vkchat.donate.spark")) return Math.max(1, (int)(cost * 0.90));
        return cost;
    }

    private int getDonateCooldown(Player p, String type) {
        int base = plugin.getConfig().getInt("teleportation." + type + ".cooldown", 60);
        if (p.hasPermission("vkchat.donate.overlord")) return Math.max(1, (int)(base * 0.15));
        if (p.hasPermission("vkchat.donate.legend")) return Math.max(1, (int)(base * 0.30));
        if (p.hasPermission("vkchat.donate.star")) return Math.max(1, (int)(base * 0.50));
        if (p.hasPermission("vkchat.donate.flame")) return Math.max(1, (int)(base * 0.65));
        if (p.hasPermission("vkchat.donate.spark")) return Math.max(1, (int)(base * 0.80));
        return base;
    }

    private int getDonateWarmup(Player p) {
        return plugin.getConfig().getInt("teleportation.warmup.delay", 3);
    }

    private int getDonateMaxHomes(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 30;
        if (p.hasPermission("vkchat.donate.legend")) return 20;
        if (p.hasPermission("vkchat.donate.star")) return 12;
        if (p.hasPermission("vkchat.donate.flame")) return 8;
        if (p.hasPermission("vkchat.donate.spark")) return 5;
        return plugin.getConfig().getInt("teleportation.home.max-homes", 3);
    }

    private boolean hasDonateStatus(Player p) {
        return getDonateDiscount(p) > 0;
    }

    private double getDonateDiscount(Player p) {
        if (p.hasPermission("vkchat.donate.overlord")) return 0.65;
        if (p.hasPermission("vkchat.donate.legend")) return 0.50;
        if (p.hasPermission("vkchat.donate.star")) return 0.35;
        if (p.hasPermission("vkchat.donate.flame")) return 0.20;
        if (p.hasPermission("vkchat.donate.spark")) return 0.10;
        return 0;
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + " сек";
        if (seconds < 3600) return (seconds / 60) + " мин " + (seconds % 60) + " сек";
        return (seconds / 3600) + " ч " + ((seconds % 3600) / 60) + " мин";
    }

    private Location findSafeLocation(Player p) {
        Random r = new Random();
        int minX = plugin.getConfig().getInt("teleportation.rtp.min-x", -5000);
        int maxX = plugin.getConfig().getInt("teleportation.rtp.max-x", 5000);
        int minZ = plugin.getConfig().getInt("teleportation.rtp.min-z", -5000);
        int maxZ = plugin.getConfig().getInt("teleportation.rtp.max-z", 5000);

        for (int attempts = 0; attempts < 25; attempts++) {
            int x = r.nextInt(maxX - minX + 1) + minX;
            int z = r.nextInt(maxZ - minZ + 1) + minZ;
            int y = p.getWorld().getHighestBlockYAt(x, z);

            if (y <= 0) continue;

            Location checkLoc = new Location(p.getWorld(), x + 0.5, y + 1, z + 0.5);

            if (isLocationSafe(checkLoc)) {
                if (Bukkit.getPluginManager().isPluginEnabled("VKChatNations")) {
                    try {
                        Class<?> pluginClazz = Class.forName("ru.example.vkchatnations.VKChatNationsPlugin");
                        Object nationsInstance = pluginClazz.getMethod("getInstance").invoke(null);
                        if (nationsInstance != null) {
                            Object manager = pluginClazz.getMethod("getNationManager").invoke(nationsInstance);
                            if (manager != null) {
                                Object claim = manager.getClass().getMethod("getChunkClaim", org.bukkit.Chunk.class).invoke(manager, checkLoc.getChunk());
                                if (claim != null) {
                                    continue;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                return checkLoc;
            }
        }
        return null;
    }

    private boolean isLocationSafe(Location loc) {
        Block feetBlock = loc.getBlock();
        Block standBlock = feetBlock.getRelative(BlockFace.DOWN);
        Block headBlock = feetBlock.getRelative(BlockFace.UP);
        Block aboveHead = headBlock.getRelative(BlockFace.UP);

        if (!standBlock.getType().isSolid()) return false;
        if (standBlock.isLiquid()) return false;
        if (standBlock.getType() == Material.MAGMA_BLOCK ||
            standBlock.getType() == Material.FIRE ||
            standBlock.getType() == Material.SOUL_FIRE ||
            standBlock.getType() == Material.CACTUS ||
            standBlock.getType() == Material.CAMPFIRE ||
            standBlock.getType() == Material.SOUL_CAMPFIRE ||
            standBlock.getType() == Material.SWEET_BERRY_BUSH ||
            standBlock.getType() == Material.WITHER_ROSE ||
            standBlock.getType() == Material.COBWEB ||
            standBlock.getType() == Material.SNOW) return false;

        if (feetBlock.getType().isSolid() || feetBlock.isLiquid()) return false;
        if (headBlock.getType().isSolid() || headBlock.isLiquid()) return false;
        if (aboveHead.getType().isSolid()) return false;

        return true;
    }

    // ==========================================
    // УСТАНОВКА ДОМА (/sethome [name])
    // ==========================================
    private void handleSetHome(Player p, String[] args) {
        String homeName = "default";
        if (args.length > 0) {
            homeName = args[0].replaceAll("[^a-zA-Z0-9а-яА-Я_]", "");
            if (homeName.isEmpty()) {
                p.sendMessage(ChatColor.RED + "❌ Недопустимое имя дома!");
                return;
            }
        }

        int maxHomes = getDonateMaxHomes(p);
        int currentCount = plugin.getTeleportManager().getHomeCount(p.getUniqueId());
        boolean alreadyExists = plugin.getTeleportManager().getHome(p.getUniqueId(), homeName) != null;

        if (!alreadyExists && currentCount >= maxHomes) {
            p.sendMessage(ChatColor.RED + "❌ Достигнут лимит точек дома! Ваш лимит: " + ChatColor.GOLD + maxHomes);
            return;
        }

        plugin.getTeleportManager().setHome(p.getUniqueId(), homeName, p.getLocation());
        p.sendMessage(ChatColor.GREEN + "✓ Точка дома '" + ChatColor.YELLOW + homeName + ChatColor.GREEN + "' успешно установлена!");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    // ==========================================
    // ТЕЛЕПОРТ ДОМОЙ (/home [name])
    // ==========================================
    private void handleHome(Player p, int vkId, String[] args) {
        Map<String, TeleportManager.HomeLocation> homes = plugin.getTeleportManager().getHomes(p.getUniqueId());
        if (homes.isEmpty()) {
            p.sendMessage(ChatColor.RED + "❌ У вас нет установленных точек дома! Создайте дом с помощью " + ChatColor.YELLOW + "/sethome");
            return;
        }

        String homeName = "default";
        if (args.length > 0) {
            homeName = args[0].toLowerCase();
        } else {
            // Если домов несколько, а имя не указано, и "default" не существует
            if (homes.size() > 1 && !homes.containsKey("default")) {
                p.sendMessage(ChatColor.YELLOW + "⚠️ Укажите имя дома. Список ваших домов: " + ChatColor.GOLD + String.join(", ", homes.keySet()));
                return;
            }
            // Если один дом и он не default, телепортируем к нему
            if (homes.size() == 1 && !homes.containsKey("default")) {
                homeName = homes.keySet().iterator().next();
            }
        }

        TeleportManager.HomeLocation homeLoc = plugin.getTeleportManager().getHome(p.getUniqueId(), homeName);
        if (homeLoc == null) {
            p.sendMessage(ChatColor.RED + "❌ Точка дома '" + ChatColor.YELLOW + homeName + ChatColor.RED + "' не найдена! Напишите " + ChatColor.YELLOW + "/homes" + ChatColor.RED + " для списка.");
            return;
        }

        Location target = homeLoc.toLocation();
        if (target == null) {
            p.sendMessage(ChatColor.RED + "❌ Ошибка: мир для этой точки дома не загружен.");
            return;
        }

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int cost = plugin.getConfig().getInt("teleportation.home.cost", 250);
        cost = Math.max(1, applyDonateDiscount(p, cost));

        int cooldown = getDonateCooldown(p, "home");

        long cdRemaining = plugin.getTeleportManager().getCooldownRemaining("home", p.getUniqueId(), cooldown);
        if (cdRemaining > 0) {
            p.sendMessage(ChatColor.RED + "⏳ Телепортация на перезарядке! Подождите " + ChatColor.GOLD + formatTime(cdRemaining));
            return;
        }

        if (currentRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации! Нужно: " + ChatColor.GOLD + cost + ChatColor.RED + " реп.");
            return;
        }

        String finalHomeName = homeName;
        plugin.getTeleportManager().startTeleportWarmup(
                p,
                target,
                ChatColor.GREEN + "✨ Телепортация в дом '" + ChatColor.YELLOW + finalHomeName + ChatColor.GREEN + "' за " + ChatColor.GOLD + cost + ChatColor.GREEN + " реп!",
                cost,
                "home",
                null
        );
    }

    // ==========================================
    // УДАЛЕНИЕ ДОМА (/delhome [name])
    // ==========================================
    private void handleDelHome(Player p, String[] args) {
        String homeName = "default";
        if (args.length > 0) {
            homeName = args[0].toLowerCase();
        }

        if (plugin.getTeleportManager().deleteHome(p.getUniqueId(), homeName)) {
            p.sendMessage(ChatColor.GREEN + "✓ Точка дома '" + ChatColor.YELLOW + homeName + ChatColor.GREEN + "' успешно удалена!");
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            p.sendMessage(ChatColor.RED + "❌ Точка дома '" + ChatColor.YELLOW + homeName + ChatColor.RED + "' не найдена.");
        }
    }

    // ==========================================
    // СПИСОК ДОМОВ (/homes)
    // ==========================================
    private void handleHomesList(Player p) {
        Map<String, TeleportManager.HomeLocation> homes = plugin.getTeleportManager().getHomes(p.getUniqueId());
        if (homes.isEmpty()) {
            p.sendMessage(ChatColor.RED + "❌ У вас нет установленных точек дома! Используйте " + ChatColor.YELLOW + "/sethome <имя>");
            return;
        }

        p.sendMessage(ChatColor.DARK_AQUA + "=== " + ChatColor.AQUA + "Ваши точки дома (" + homes.size() + "/" + plugin.getConfig().getInt("teleportation.home.max-homes", 3) + ")" + ChatColor.DARK_AQUA + " ===");
        for (Map.Entry<String, TeleportManager.HomeLocation> entry : homes.entrySet()) {
            TeleportManager.HomeLocation loc = entry.getValue();
            p.sendMessage(ChatColor.GOLD + "• " + ChatColor.YELLOW + entry.getKey() + 
                    ChatColor.GRAY + " [Мир: " + loc.worldName + " | " + (int)loc.x + ", " + (int)loc.y + ", " + (int)loc.z + "]");
        }
    }

    // ==========================================
    // ЗАПРОС НА ТЕЛЕПОРТ (/tpa <player>)
    // ==========================================
    private void handleTpa(Player p, int vkId, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ChatColor.RED + "❌ Использование: " + ChatColor.YELLOW + "/tpa <игрок>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(ChatColor.RED + "❌ Игрок не найден или не в сети!");
            return;
        }

        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "❌ Вы не можете телепортироваться к самому себе!");
            return;
        }

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int cost = plugin.getConfig().getInt("teleportation.tpa.cost", 250);
        cost = Math.max(1, applyDonateDiscount(p, cost));

        int cooldown = getDonateCooldown(p, "tpa");

        long cdRemaining = plugin.getTeleportManager().getCooldownRemaining("tpa", p.getUniqueId(), cooldown);
        if (cdRemaining > 0) {
            p.sendMessage(ChatColor.RED + "⏳ Запрос на перезарядке! Подождите " + ChatColor.GOLD + formatTime(cdRemaining));
            return;
        }

        if (currentRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации! Нужно: " + ChatColor.GOLD + cost + ChatColor.RED + " реп.");
            return;
        }

        plugin.getTeleportManager().sendTpaRequest(p, target);

        p.sendMessage(ChatColor.GREEN + "✓ Запрос на телепортацию отправлен игроку " + ChatColor.YELLOW + target.getName() + ".");
        target.sendMessage(ChatColor.GOLD + "✉ " + ChatColor.YELLOW + p.getName() + " хочет телепортироваться к вам.");
        target.sendMessage(ChatColor.YELLOW + "Используйте " + ChatColor.GREEN + "/tpaccept" + ChatColor.YELLOW + " для подтверждения или " + ChatColor.RED + "/tpdeny" + ChatColor.YELLOW + " для отказа.");
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
    }

    // ==========================================
    // ПОДТВЕРЖДЕНИЕ ЗАПРОСА (/tpaccept)
    // ==========================================
    private void handleTpAccept(Player p) {
        ru.example.vkchatteleport.manager.TeleportManager.TpaRequestInfo requestInfo =
                plugin.getTeleportManager().getTpaRequestInfo(p.getUniqueId());
        if (requestInfo == null) {
            p.sendMessage(ChatColor.RED + "❌ У вас нет активных запросов на телепортацию.");
            return;
        }

        Player senderPlayer = Bukkit.getPlayer(requestInfo.sender);
        if (senderPlayer == null || !senderPlayer.isOnline()) {
            p.sendMessage(ChatColor.RED + "❌ Отправитель запроса вышел из игры.");
            plugin.getTeleportManager().clearTpaRequest(p.getUniqueId());
            return;
        }

        int senderVk = VKChatPlugin.getInstance().getApi().getLinkedVkId(senderPlayer);
        if (senderVk == -1) {
            p.sendMessage(ChatColor.RED + "❌ Отправитель не привязал ВКонтакте.");
            senderPlayer.sendMessage(ChatColor.RED + "❌ Ваш запрос был отменен — вы не привязали ВКонтакте!");
            plugin.getTeleportManager().clearTpaRequest(p.getUniqueId());
            return;
        }

        int senderRep = VKChatPlugin.getInstance().getApi().getReputation(senderVk);
        int cost = (int) Math.ceil(senderRep * 0.02);
        if (cost < 10) cost = 10;
        cost = Math.min(senderRep, applyDonateDiscount(senderPlayer, cost));

        if (senderRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ У отправителя больше нет необходимой репутации.");
            senderPlayer.sendMessage(ChatColor.RED + "❌ Недостаточно репутации (нужно " + cost + ").");
            plugin.getTeleportManager().clearTpaRequest(p.getUniqueId());
            return;
        }

        p.sendMessage(ChatColor.GREEN + "✓ Вы приняли запрос от " + ChatColor.YELLOW + senderPlayer.getName() + ".");
        plugin.getTeleportManager().clearTpaRequest(p.getUniqueId());

        if (requestInfo.isHere) {
            // /tpahere: цель (p) телепортируется к отправителю (senderPlayer)
            plugin.getTeleportManager().startTeleportWarmup(
                    p,
                    senderPlayer.getLocation(),
                    ChatColor.GREEN + "✨ Вы телепортировались к " + ChatColor.YELLOW + senderPlayer.getName() + " за " + ChatColor.GOLD + cost + ChatColor.GREEN + " реп. ВК!",
                    cost,
                    "tpahere",
                    () -> senderPlayer.sendMessage(ChatColor.GREEN + "✨ " + ChatColor.YELLOW + p.getName() + ChatColor.GREEN + " телепортировался к вам.")
            );
        } else {
            // /tpa: отправитель телепортируется к цели (текущее поведение)
            plugin.getTeleportManager().startTeleportWarmup(
                    senderPlayer,
                    p.getLocation(),
                    ChatColor.GREEN + "✨ Вы телепортировались к " + ChatColor.YELLOW + p.getName() + " за " + ChatColor.GOLD + cost + ChatColor.GREEN + " реп. ВК!",
                    cost,
                    "tpa",
                    () -> p.sendMessage(ChatColor.GREEN + "✨ " + ChatColor.YELLOW + senderPlayer.getName() + ChatColor.GREEN + " телепортировался к вам.")
            );
        }
    }

    // ==========================================
    // ОТМЕНА ОТПРАВЛЕННОГО ЗАПРОСА (/tpa cancel)
    // ==========================================
    private void handleTpaCancel(Player p) {
        UUID targetId = plugin.getTeleportManager().cancelOutgoingTpa(p.getUniqueId());
        if (targetId == null) {
            p.sendMessage(ChatColor.RED + "❌ У вас нет активных исходящих запросов на телепортацию.");
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        p.sendMessage(ChatColor.YELLOW + "✕ Вы отменили запрос на телепортацию.");
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.RED + "✕ " + ChatColor.YELLOW + p.getName() + ChatColor.RED + " отменил запрос на телепортацию.");
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
    }

    // ==========================================
    // ЗАПРОС ТЕЛЕПОРТА К СЕБЕ (/tpahere <player>)
    // ==========================================
    private void handleTpaHere(Player p, int vkId, String[] args) {
        if (args.length < 1) {
            p.sendMessage(ChatColor.RED + "❌ Использование: " + ChatColor.YELLOW + "/tpahere <игрок>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            p.sendMessage(ChatColor.RED + "❌ Игрок не найден или не в сети!");
            return;
        }

        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "❌ Вы не можете отправить запрос самому себе!");
            return;
        }

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int cost = plugin.getConfig().getInt("teleportation.tpahere.cost", 150);
        cost = Math.max(1, applyDonateDiscount(p, cost));

        int cooldown = getDonateCooldown(p, "tpahere");

        long cdRemaining = plugin.getTeleportManager().getCooldownRemaining("tpahere", p.getUniqueId(), cooldown);
        if (cdRemaining > 0) {
            p.sendMessage(ChatColor.RED + "⏳ Запрос на перезарядке! Подождите " + ChatColor.GOLD + formatTime(cdRemaining));
            return;
        }

        if (currentRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации! Нужно: " + ChatColor.GOLD + cost + ChatColor.RED + " реп.");
            return;
        }

        plugin.getTeleportManager().sendTpaHereRequest(p, target);

        p.sendMessage(ChatColor.GREEN + "✓ Запрос на телепортацию к вам отправлен игроку " + ChatColor.YELLOW + target.getName() + ".");
        target.sendMessage(ChatColor.GOLD + "✉ " + ChatColor.YELLOW + p.getName() + " хочет телепортировать вас к себе.");
        target.sendMessage(ChatColor.YELLOW + "Используйте " + ChatColor.GREEN + "/tpaccept" + ChatColor.YELLOW + " для подтверждения или " + ChatColor.RED + "/tpdeny" + ChatColor.YELLOW + " для отказа.");
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
    }

    // ==========================================
    // ВОЗВРАТ К МЕСТУ СМЕРТИ (/back)
    // ==========================================
    private void handleBack(Player p, int vkId) {
        Location deathLoc = plugin.getTeleportManager().getDeathLocation(p.getUniqueId());
        if (deathLoc == null) {
            p.sendMessage(ChatColor.RED + "❌ Нет сохраненной точки смерти! Используйте после возрождения.");
            return;
        }

        int currentRep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
        int cost = plugin.getConfig().getInt("teleportation.back.cost", 50);
        cost = Math.max(1, applyDonateDiscount(p, cost));

        int cooldown = getDonateCooldown(p, "back");

        long cdRemaining = plugin.getTeleportManager().getCooldownRemaining("back", p.getUniqueId(), cooldown);
        if (cdRemaining > 0) {
            p.sendMessage(ChatColor.RED + "⏳ Подождите " + ChatColor.GOLD + formatTime(cdRemaining));
            return;
        }

        if (currentRep < cost) {
            p.sendMessage(ChatColor.RED + "❌ Нужно " + ChatColor.GOLD + cost + ChatColor.RED + " реп.");
            return;
        }

        plugin.getTeleportManager().startTeleportWarmup(
                p,
                deathLoc,
                ChatColor.GREEN + "⚰ Вы вернулись к месту смерти за " + ChatColor.GOLD + cost + ChatColor.GREEN + " реп!",
                cost,
                "back",
                null
        );
    }

    // ==========================================
    // ИСТОРИЯ ТЕЛЕПОРТАЦИЙ (/tphistory)
    // ==========================================
    private void handleTpHistory(Player p) {
        java.util.List<ru.example.vkchatteleport.manager.TeleportManager.TeleportHistoryEntry> history =
                plugin.getTeleportManager().getTeleportHistory(p.getUniqueId());

        if (history.isEmpty()) {
            p.sendMessage(ChatColor.GRAY + "История телепортаций пуста.");
            return;
        }

        p.sendMessage(ChatColor.DARK_AQUA + "=== " + ChatColor.AQUA + "История телепортаций (" + history.size() + ")" + ChatColor.DARK_AQUA + " ===");
        int index = 1;
        for (ru.example.vkchatteleport.manager.TeleportManager.TeleportHistoryEntry entry : history) {
            p.sendMessage(ChatColor.GOLD + "#" + index + ChatColor.WHITE + " [" + entry.getFormattedTime() + "] " +
                    ChatColor.YELLOW + entry.type.toUpperCase() + ChatColor.GRAY + " | " +
                    entry.fromWorld.replace("world", "Мир") + " " +
                    ChatColor.WHITE + "(" + (int)entry.fromX + "," + (int)entry.fromY + "," + (int)entry.fromZ + ")" +
                    ChatColor.GRAY + " → " +
                    entry.toWorld.replace("world", "Мир") + " " +
                    ChatColor.WHITE + "(" + (int)entry.toX + "," + (int)entry.toY + "," + (int)entry.toZ + ")");
            index++;
        }
    }
    // ==========================================
    // ОТКЛОНЕНИЕ ЗАПРОСА (/tpdeny)
    // ==========================================
    private void handleTpDeny(Player p) {
        UUID senderId = plugin.getTeleportManager().getTpaRequest(p.getUniqueId());
        if (senderId == null) {
            p.sendMessage(ChatColor.RED + "❌ У вас нет активных запросов на телепортацию.");
            return;
        }

        Player senderPlayer = Bukkit.getPlayer(senderId);
        plugin.getTeleportManager().clearTpaRequest(p.getUniqueId());

        p.sendMessage(ChatColor.RED + "✕ Вы отклонили запрос на телепортацию.");
        if (senderPlayer != null && senderPlayer.isOnline()) {
            senderPlayer.sendMessage(ChatColor.RED + "✕ " + ChatColor.YELLOW + p.getName() + ChatColor.RED + " отклонил ваш запрос на телепортацию.");
            senderPlayer.playSound(senderPlayer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("tpa") && args.length == 1) {
            completions.add("cancel");
            for (Player online : Bukkit.getOnlinePlayers()) {
                completions.add(online.getName());
            }
        } else if (cmd.equals("tpahere") && args.length == 1) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                completions.add(online.getName());
            }
        } else if (cmd.equals("home") && args.length == 1) {
            if (sender instanceof Player) {
                Map<String, TeleportManager.HomeLocation> homes = plugin.getTeleportManager().getHomes(((Player) sender).getUniqueId());
                completions.addAll(homes.keySet());
            }
        } else if (cmd.equals("delhome") && args.length == 1) {
            if (sender instanceof Player) {
                Map<String, TeleportManager.HomeLocation> homes = plugin.getTeleportManager().getHomes(((Player) sender).getUniqueId());
                completions.addAll(homes.keySet());
            }
        } else if (cmd.equals("sethome") && args.length == 1) {
            if (sender instanceof Player) {
                Map<String, TeleportManager.HomeLocation> homes = plugin.getTeleportManager().getHomes(((Player) sender).getUniqueId());
                completions.addAll(homes.keySet());
            }
        } else if ((cmd.equals("gateway") || cmd.equals("portal")) && args.length == 1) {
            completions.addAll(java.util.Arrays.asList("soviet", "pagan", "imperial"));
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }
}
