package ru.example.vkchatnations.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchatnations.data.WarManager;
import ru.example.vkchat.util.VKChatBridge;
import ru.example.vkchat.util.UUIDResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class NationCommand implements CommandExecutor, TabCompleter {
    private final VKChatNationsPlugin plugin;
    private final java.util.Map<UUID, Long> tpCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    public NationCommand(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Команда reload доступна и консоли
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vkchat.nations.admin") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "✓ Конфиг наций перезагружен.");
            return true;
        }

        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            plugin.getGuiListener().openGui(p);
            return true;
        }

        String action = args[0].toLowerCase();

        // ═══ ADMIN COMMANDS ═══
        if (action.equals("admin") && args.length >= 2) {
            if (!p.hasPermission("vkchat.nations.admin") && !p.isOp()) {
                p.sendMessage(ChatColor.RED + "Нет прав.");
                return true;
            }
            String sub = args[1].toLowerCase();
            if (sub.equals("setnation") && args.length >= 4) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { p.sendMessage(ChatColor.RED + "Игрок не найден."); return true; }
                String newNation = args[3];
                plugin.getNationManager().setPlayerNation(target, newNation);
                p.sendMessage(ChatColor.GREEN + "✓ " + target.getName() + " → нация " + newNation);
                target.sendMessage(ChatColor.GREEN + "✓ Админ сменил вашу нацию на " + newNation);
                return true;
            }
            if (sub.equals("list")) {
                p.sendMessage(ChatColor.GOLD + "=== Приваты ===");
                for (java.util.Map.Entry<String, ChunkClaim> e : plugin.getNationManager().getNationClaims().entrySet()) {
                    ChunkClaim c = e.getValue();
                    String ownerN = Bukkit.getOfflinePlayer(c.getOwner()).getName();
                    p.sendMessage(ChatColor.GRAY + "  " + c.getName() + " — " + (ownerN != null ? ownerN : c.getOwner().toString().substring(0, 8))
                            + " | " + c.getWorldName() + " " + c.getX() + "," + c.getZ() + " | lvl " + c.getLevel() + " dur " + c.getDurability());
                }
                return true;
            }
            if (sub.equals("removeclaim") && args.length >= 3) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { p.sendMessage(ChatColor.RED + "Игрок не найден."); return true; }
                String keyToRemove = null;
                for (java.util.Map.Entry<String, ChunkClaim> e : plugin.getNationManager().getNationClaims().entrySet()) {
                    if (e.getValue().getOwner().equals(target.getUniqueId())) {
                        keyToRemove = e.getKey();
                        break;
                    }
                }
                if (keyToRemove != null) {
                    plugin.getNationManager().getNationClaims().remove(keyToRemove);
                    plugin.getNationManager().saveAll();
                    p.sendMessage(ChatColor.GREEN + "✓ Приват " + target.getName() + " удалён.");
                } else {
                    p.sendMessage(ChatColor.RED + "У игрока нет приватов.");
                }
                return true;
            }
            if (sub.equals("reload")) {
                plugin.reloadConfig();
                p.sendMessage(ChatColor.GREEN + "✓ Конфиг перезагружен.");
                return true;
            }
            p.sendMessage(ChatColor.RED + "Подкоманды: setnation <player> <nation>, list, removeclaim <player>, reload");
            return true;
        }

        if (action.equals("buyclaim") || action.equals("buy")) {
            plugin.getGuiListener().openClaimShop(p);
            return true;
        }

        else if (action.equals("feed") || action.equals("feedclaim")) {
            ru.example.vkchatnations.data.ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
            if (claim != null && claim.getOwner().equals(p.getUniqueId())) {
                plugin.getGuiListener().openClaimFeedGui(p, claim);
            } else {
                p.sendMessage(ChatColor.RED + "❌ Вы должны находиться внутри собственного привата, чтобы подпитать его!");
            }
            return true;
        }

        else if (action.equals("claim")) {
            p.sendMessage(ChatColor.RED + "❌ Команда /n claim упразднена! Приваты теперь создаются путем установки блока привата.");
            p.sendMessage(ChatColor.GRAY + "Вы можете приобрести блоки привата разного радиуса через команду: " + ChatColor.YELLOW + "/n buyclaim");
        } 

        else if (action.equals("unclaim")) {
            p.sendMessage(ChatColor.RED + "❌ Команда /n unclaim упразднена! Чтобы удалить приват, просто сломайте ваш блок привата.");
        }

        else if (action.equals("autoclaim")) {
            p.sendMessage(ChatColor.RED + "❌ Авто-приват упразднен! Приваты создаются путем установки блока привата.");
        }

        else if (action.equals("sethome")) {
            ru.example.vkchatnations.data.ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
            if (claim != null && claim.getOwner().equals(p.getUniqueId())) {
                claim.setHome(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
                plugin.getNationManager().saveAll();
                p.sendMessage(ChatColor.GREEN + "✓ Точка возрождения в привате успешно установлена на ваших текущих координатах!");
            } else {
                p.sendMessage(ChatColor.RED + "❌ Вы должны находиться внутри своего привата, чтобы установить точку возрождения!");
            }
        }

        else if (action.equals("home")) {
            ru.example.vkchatnations.data.ChunkClaim claim = plugin.getNationManager().getPlayerHomeClaim(p.getUniqueId());
            if (claim == null) {
                p.sendMessage(ChatColor.RED + "❌ У вас нет установленной точки возрождения в приватах! Используйте /nation sethome в своем привате.");
                return true;
            }
            
            long last = tpCooldowns.getOrDefault(p.getUniqueId(), 0L);
            if (System.currentTimeMillis() - last < 300000) {
                long left = (300000 - (System.currentTimeMillis() - last)) / 1000;
                p.sendMessage(ChatColor.RED + "Телепортация на кулдауне! Осталось: " + left + " сек.");
                return true;
            }

            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink), чтобы телепортироваться за репутацию!");
                return true;
            }

            int cost = plugin.getConfig().getInt("claim.teleport-cost", 20);
            if (VKChatBridge.getReputation(vkId) < cost) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации ВК! Требуется: " + cost);
                return true;
            }

            VKChatBridge.takeReputation(vkId, cost);
            tpCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
            
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(claim.getWorldName());
            if (w == null) { p.sendMessage(ChatColor.RED + "Мир привата не найден!"); return true; }
            org.bukkit.Location target = new org.bukkit.Location(w, claim.getHomeX(), claim.getHomeY(), claim.getHomeZ(), p.getLocation().getYaw(), p.getLocation().getPitch());
            p.teleport(target);
            p.sendMessage(ChatColor.GREEN + "✓ Вы телепортировались на точку возрождения привата! Списано " + cost + " репутации.");
        } 

        else if (action.equals("claims") || action.equals("list")) {
            p.sendMessage(ChatColor.DARK_AQUA + "=== Ваши блоки привата ===");
            int count = 0;
            for (java.util.Map.Entry<String, ru.example.vkchatnations.data.ChunkClaim> entry : plugin.getNationManager().getNationClaims().entrySet()) {
                if (entry.getValue().getOwner().equals(p.getUniqueId())) {
                    String[] parts = entry.getKey().split(";"); // world;x;y;z
                    if (parts.length >= 4) {
                        String world = parts[0];
                        int blockX = Integer.parseInt(parts[1]);
                        int blockY = Integer.parseInt(parts[2]);
                        int blockZ = Integer.parseInt(parts[3]);
                        int radius = entry.getValue().getRadius();
                        
                        // Создаем кликабельное сообщение для быстрой телепортации к блоку привата!
                        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                            ChatColor.GOLD + "• " + ChatColor.YELLOW + "Радиус: " + radius + 
                            ChatColor.GRAY + " [Координаты: " + ChatColor.AQUA + "X: " + blockX + ", Y: " + blockY + ", Z: " + blockZ + ChatColor.GRAY + "]"
                        );
                        msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/nation tp " + blockX + " " + blockY + " " + blockZ));
                        msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(ChatColor.GREEN + "▶ Нажмите для телепортации за 20 реп. ВК ◀")));
                        p.spigot().sendMessage(msg);
                        count++;
                    }
                }
            }
            if (count == 0) {
                p.sendMessage(ChatColor.RED + "У вас нет заприваченных земель! Купите блок привата в " + ChatColor.YELLOW + "/n buyclaim");
            } else {
                p.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.ITALIC + "Кликните по любой строке в списке выше, чтобы телепортироваться к блоку за 20 реп. ВК!");
            }
            return true;
        }

        else if (action.equals("tp") || action.equals("teleport")) {
            if (args.length < 4) {
                p.sendMessage(ChatColor.RED + "❌ Использование: /nation tp <X> <Y> <Z>");
                return true;
            }
            
            int blockX, blockY, blockZ;
            try {
                blockX = Integer.parseInt(args[1]);
                blockY = Integer.parseInt(args[2]);
                blockZ = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                p.sendMessage(ChatColor.RED + "❌ Координаты должны быть числами!");
                return true;
            }

            String worldName = p.getWorld().getName();
            String key = worldName + ";" + blockX + ";" + blockY + ";" + blockZ;
            ru.example.vkchatnations.data.ChunkClaim claim = plugin.getNationManager().getNationClaims().get(key);
            
            if (claim == null) {
                p.sendMessage(ChatColor.RED + "❌ Блок привата по этим координатам не найден.");
                return true;
            }

            if (!claim.getOwner().equals(p.getUniqueId()) && !p.hasPermission("vkchat.admin")) {
                p.sendMessage(ChatColor.RED + "❌ Вы не являетесь владельцем этого привата!");
                return true;
            }

            // Проверка кулдауна
            long last = tpCooldowns.getOrDefault(p.getUniqueId(), 0L);
            if (System.currentTimeMillis() - last < 300000) {
                long left = (300000 - (System.currentTimeMillis() - last)) / 1000;
                p.sendMessage(ChatColor.RED + "⏳ Телепортация на перезарядке! Подождите " + left + " сек.");
                return true;
            }

            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте! (/vklink)");
                return true;
            }

            int cost = plugin.getConfig().getInt("claim.teleport-cost", 20);
            if (VKChatBridge.getReputation(vkId) < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " реп.");
                return true;
            }

            // Списываем репутацию и обновляем кулдаун
            VKChatBridge.takeReputation(vkId, cost);
            tpCooldowns.put(p.getUniqueId(), System.currentTimeMillis());

            // Вычисляем безопасную точку приземления над блоком привата
            org.bukkit.World tw = org.bukkit.Bukkit.getWorld(claim.getWorldName());
            if (tw == null) { p.sendMessage(ChatColor.RED + "Мир привата не найден!"); return true; }
            org.bukkit.Location target = new org.bukkit.Location(tw, blockX + 0.5, blockY + 1.0, blockZ + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());

            p.teleport(target);
            p.sendMessage(ChatColor.GREEN + "✨ Вы успешно телепортировались к своему блоку привата [" + blockX + ", " + blockY + ", " + blockZ + "] за " + cost + " репутации ВК!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            return true;
        }

        else if (action.equals("change") || action.equals("reset")) {
            String currentNation = plugin.getNationManager().getPlayerNation(p);
            if (currentNation == null) {
                p.sendMessage(ChatColor.RED + "❌ Вы еще не выбрали Нацию! Воспользуйтесь меню выбора.");
                return true;
            }

            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте (/vklink), чтобы изменить Нацию!");
                return true;
            }

            int cost = 5000;
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Смена нации/расы — это величайший ритуал, который стоит " + ChatColor.GOLD + cost + ChatColor.RED + " реп. ВК!");
                p.sendMessage(ChatColor.GRAY + "Ваш баланс: " + ChatColor.YELLOW + rep + ChatColor.GRAY + " реп. ВК.");
                return true;
            }

            // Списываем репутацию
            VKChatBridge.takeReputation(vkId, cost);

            // Удаляем все приваты игрока
            p.sendMessage(ChatColor.YELLOW + "⌛ Запуск очистки ваших приватизированных блоков...");
            int clearedCount = 0;
            java.util.Iterator<java.util.Map.Entry<String, ru.example.vkchatnations.data.ChunkClaim>> iterator = plugin.getNationManager().getNationClaims().entrySet().iterator();
            while (iterator.hasNext()) {
                java.util.Map.Entry<String, ru.example.vkchatnations.data.ChunkClaim> entry = iterator.next();
                if (entry.getValue().getOwner().equals(p.getUniqueId())) {
                    // Пытаемся физически удалить блок привата из мира
                    ru.example.vkchatnations.data.ChunkClaim c = entry.getValue();
                    org.bukkit.World w = org.bukkit.Bukkit.getWorld(c.getWorldName());
                    if (w != null) {
                        org.bukkit.block.Block b = w.getBlockAt(c.getX(), c.getY(), c.getZ());
                        if (b.getType() == org.bukkit.Material.GOLD_BLOCK || b.getType() == org.bukkit.Material.EMERALD_BLOCK || b.getType() == org.bukkit.Material.DIAMOND_BLOCK) {
                            b.setType(org.bukkit.Material.AIR);
                        }
                    }
                    iterator.remove();
                    clearedCount++;
                }
            }

            // Сбрасываем нацию игрока
            plugin.getNationManager().removePlayerNation(p.getUniqueId());

            p.sendMessage(" ");
            p.sendMessage(ChatColor.RED + "⚠️ [Смена Расы] Вы отреклись от своей старой Нации!");
            p.sendMessage(ChatColor.GRAY + "  • Списано: " + ChatColor.GOLD + cost + " реп. ВК");
            p.sendMessage(ChatColor.GRAY + "  • Удалено ваших приватов: " + ChatColor.RED + clearedCount);
            p.sendMessage(ChatColor.GRAY + "  • Все ваши точки возрождения в приватах сброшены.");
            p.sendMessage(" ");

            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 1f, 1f);
            p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, p.getLocation().add(0, 1, 0), 3);

            // Открываем меню выбора нации
            plugin.getGuiListener().openNationSelection(p);
            return true;
        }

        else if (action.equals("festival") || action.equals("party")) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "❌ Сначала выберите Нацию! (/nation)");
                return true;
            }

            int cost = 1000;
            int bank = plugin.getNationManager().getBank(nation);
            if (bank < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации в Казне Нации! Требуется " + cost + " реп. (В казне: " + bank + " реп.).");
                p.sendMessage(ChatColor.GRAY + "💡 Пополнить казну можно через взносы жителей.");
                return true;
            }

            // Списываем репутацию
            plugin.getNationManager().depositReputation(nation, -cost);

            plugin.getNationManager().setFestivalEndTime(nation, System.currentTimeMillis() + 86400000L); // 24 часа

            plugin.getNationManager().broadcastToNationWithPrefix(nation, 
                "§a🎉🎉 [НАЦИОНАЛЬНЫЙ ФЕСТИВАЛЬ] §eОбъявлен великий праздник на 24 часа! Все граждане нашей Нации получают постоянную §bСпешку§e и §aУдачу§e!"
            );
            
            for (Player online : Bukkit.getOnlinePlayers()) {
                String pNation = plugin.getNationManager().getPlayerNation(online);
                if (nation.equals(pNation)) {
                    online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                }
            }
            return true;
        }

        else if (action.equals("trust")) {
            if (args.length < 2) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Использование: /nation trust <ник>");
                return true;
            }
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[1]);
            if (target == null) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Игрок не найден!");
                return true;
            }
            ru.example.vkchatnations.data.ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
            if (claim != null && claim.getOwner().equals(p.getUniqueId())) {
                claim.addTrusted(target.getUniqueId());
                plugin.getNationManager().saveAll();
                p.sendMessage(org.bukkit.ChatColor.GREEN + "Игрок " + target.getName() + " добавлен в доверенные для этого привата!");
            } else {
                p.sendMessage(org.bukkit.ChatColor.RED + "Вы должны стоять внутри своей приваченной области!");
            }
        }
        else if (action.equals("untrust")) {
            if (args.length < 2) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Использование: /nation untrust <ник>");
                return true;
            }
            org.bukkit.OfflinePlayer target = UUIDResolver.resolve(args[1]);
            if (target == null) {
                p.sendMessage(org.bukkit.ChatColor.RED + "Игрок " + args[1] + " не найден.");
                return true;
            }
            ru.example.vkchatnations.data.ChunkClaim claim = plugin.getNationManager().getClaimAt(p.getLocation());
            if (claim != null && claim.getOwner().equals(p.getUniqueId())) {
                claim.removeTrusted(target.getUniqueId());
                plugin.getNationManager().saveAll();
                p.sendMessage(org.bukkit.ChatColor.YELLOW + "Игрок удален из доверенных для этого привата.");
            } else {
                p.sendMessage(org.bukkit.ChatColor.RED + "Вы должны стоять внутри своей приваченной области!");
            }
        }

        else if (action.equals("charge")) {
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "Сначала привяжи ВКонтакте (/vklink)!");
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(ChatColor.YELLOW + "Использование: /nation charge <сумма> — пожертвовать реп. в казну");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                p.sendMessage(ChatColor.RED + "Сумма должна быть числом!");
                return true;
            }
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < amount) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации! У вас: " + rep + " реп. ВК");
                return true;
            }
            VKChatBridge.takeReputation(vkId, amount);
            plugin.getNationManager().depositReputation(nation, amount);
            plugin.getNationManager().addContribution(p.getUniqueId(), amount);
            plugin.getNationManager().addNationExp(nation, amount);
            p.sendMessage(ChatColor.GREEN + "✓ Вы пополнили казну нации на " + amount + " реп. ВК!");
            p.sendMessage(ChatColor.GRAY + "Баланс казны: " + ChatColor.YELLOW + plugin.getNationManager().getBank(nation) + " реп.");
        }

        else if (action.equals("info")) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }

            String colorCode = plugin.getConfig().getString("nations." + nation + ".color", "&f");
            String cc = ChatColor.translateAlternateColorCodes('&', colorCode);
            String rawName = plugin.getNationManager().getNationNamePublic(nation);
            String stripName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawName));

            int bank = plugin.getNationManager().getBank(nation);
            int level = plugin.getNationManager().getNationLevel(nation);
            int totalMembers = plugin.getNationManager().getMemberCount(nation);

            int claimCount = 0;
            for (ru.example.vkchatnations.data.ChunkClaim c : plugin.getNationManager().getNationClaims().values()) {
                if (c.getNation().equals(nation)) claimCount++;
            }

            int onlineCount = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (nation.equals(plugin.getNationManager().getPlayerNation(online))) onlineCount++;
            }

            List<String[]> activeWars = plugin.getWarManager().getActiveWarsFor(nation);
            List<String> bonuses = plugin.getConfig().getStringList("nations." + nation + ".bonuses");
            int contribution = plugin.getNationManager().getContribution(p.getUniqueId());
            int rank = getNationRank(plugin, nation);

            p.sendMessage("§8▸ " + cc + "§l" + stripName + " §8◂ §7Информация о нации");
            p.sendMessage("§7─────────────────────────");
            p.sendMessage("§8▸ §fУровень: " + cc + level + " " + plugin.getNationManager().getNationProgressBar(nation));
            p.sendMessage("§8▸ §fУчастники: §a" + onlineCount + "§7/§f" + totalMembers + " §7онлайн");
            p.sendMessage("§8▸ §fПриваты: §e" + claimCount);
            p.sendMessage("§8▸ §fКазна: §6" + bank + " реп.");
            p.sendMessage("§8▸ §fРейтинг: §b#" + rank);

            if (activeWars.isEmpty()) {
                p.sendMessage("§8▸ §fВойны: §7нет");
            } else {
                StringBuilder warLine = new StringBuilder("§8▸ §fВойны: ");
                for (int i = 0; i < activeWars.size(); i++) {
                    String[] wn = activeWars.get(i);
                    String enemyId = wn[0].equals(nation) ? wn[1] : wn[0];
                    String enemyName = plugin.getNationManager().getNationNamePublic(enemyId);
                    long endTime = plugin.getWarManager().getWarEndTime(wn[0], wn[1]);
                    long mins = (endTime - System.currentTimeMillis()) / 60000;
                    if (i > 0) warLine.append("§7, ");
                    warLine.append("§c⚔ ").append(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', enemyName)))
                            .append(" §7(").append(mins).append(" мин.)");
                }
                p.sendMessage(warLine.toString());
            }

            if (!bonuses.isEmpty()) {
                StringBuilder bonusLine = new StringBuilder("§8▸ §fБонусы: ");
                for (int i = 0; i < bonuses.size(); i++) {
                    String[] parts = bonuses.get(i).split(";");
                    if (parts.length == 2) {
                        String effectName = formatEffectName(parts[0]);
                        int lvl = Integer.parseInt(parts[1]);
                        if (i > 0) bonusLine.append("§7, ");
                        bonusLine.append("§b").append(effectName).append(" ").append(toRoman(lvl));
                    }
                }
                p.sendMessage(bonusLine.toString());
            } else {
                p.sendMessage("§8▸ §fБонусы: §7нет");
            }

            p.sendMessage("§8▸ §fВаш вклад: §e" + contribution + " реп.");
            p.sendMessage("§7─────────────────────────");
        }

        else if (action.equals("top")) {
            p.sendMessage(ChatColor.GOLD + "=== Топ наций по казне ===");
            java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>();
            for (String n : plugin.getConfig().getConfigurationSection("nations").getKeys(false)) {
                sorted.add(new java.util.AbstractMap.SimpleEntry<>(n, plugin.getNationManager().getBank(n)));
            }
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            int rank = 1;
            for (java.util.Map.Entry<String, Integer> entry : sorted) {
                String name = plugin.getNationManager().getNationNamePublic(entry.getKey());
                ChatColor color = rank <= 3 ? ChatColor.GOLD : ChatColor.GRAY;
                p.sendMessage(color + "#" + rank + " " + name + ": " + ChatColor.WHITE + entry.getValue() + " реп.");
                rank++;
            }
        }

        else if (action.equals("members")) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }
            String nationName = plugin.getNationManager().getNationNamePublic(nation);
            p.sendMessage(ChatColor.GOLD + "=== Участники нации " + nationName + ChatColor.GOLD + " ===");
            int count = 0;
            for (java.util.Map.Entry<UUID, String> entry : plugin.getNationManager().getPlayerNations().entrySet()) {
                if (nation.equals(entry.getValue())) {
                    count++;
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(entry.getKey());
                    String status = op.isOnline() ? ChatColor.GREEN + " ● Онлайн" : ChatColor.GRAY + " ○ Офлайн";
                    p.sendMessage(ChatColor.YELLOW + "  " + op.getName() + status);
                }
            }
            p.sendMessage(ChatColor.GRAY + "Всего участников: " + ChatColor.WHITE + count);
            return true;
        }

        else if (action.equals("donate")) {
            if (args.length < 2) {
                p.sendMessage(ChatColor.RED + "Использование: /nation donate <сумма>");
                return true;
            }
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                p.sendMessage(ChatColor.RED + "Сумма должна быть числом!");
                return true;
            }
            if (amount < 1) {
                p.sendMessage(ChatColor.RED + "Минимальная сумма пожертвования: 1 реп.");
                return true;
            }
            int vkId = VKChatBridge.getLinkedVkId(p);
            if (!VKChatBridge.hasVkOrPass(p)) {
                p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink)!");
                return true;
            }
            int rep = VKChatBridge.getReputation(vkId);
            if (rep < amount) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации! У вас: " + rep + " реп. ВК");
                return true;
            }
            VKChatBridge.takeReputation(vkId, amount);
            plugin.getNationManager().depositReputation(nation, amount);
            plugin.getNationManager().addContribution(p.getUniqueId(), amount);
            p.sendMessage(ChatColor.GREEN + "✓ Вы пожертвовали " + amount + " реп. ВК в казну нации!");
            p.sendMessage(ChatColor.GRAY + "Текущий баланс казны: " + ChatColor.YELLOW + plugin.getNationManager().getBank(nation) + " реп.");
            plugin.getNationManager().broadcastToNationWithPrefix(nation,
                ChatColor.WHITE + p.getName() + ChatColor.GREEN + " пожертвовал " + amount + " реп. ВК в казну нации!");
            return true;
        }

        else if (action.equals("leave")) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }
            // Проверяем, есть ли приваты
            boolean hasClaims = false;
            for (java.util.Map.Entry<String, ru.example.vkchatnations.data.ChunkClaim> entry : plugin.getNationManager().getNationClaims().entrySet()) {
                if (entry.getValue().getOwner().equals(p.getUniqueId())) {
                    hasClaims = true;
                    break;
                }
            }
            if (hasClaims) {
                p.sendMessage(ChatColor.RED + "Сначала удалите все приваты! (/nation claims)");
                return true;
            }
            plugin.getNationManager().removePlayerNation(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Вы покинули нацию! Теперь выберите новую через /nation");
            plugin.getGuiListener().openNationSelection(p);
            return true;
        }

        else if (action.equals("war")) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(ChatColor.GOLD + "=== Команды войны ===");
                p.sendMessage(ChatColor.YELLOW + "/nation war declare <нация>" + ChatColor.GRAY + " — объявить войну (5000 реп.)");
                p.sendMessage(ChatColor.YELLOW + "/nation war peace" + ChatColor.GRAY + " — заключить мир (2000 реп.)");
                p.sendMessage(ChatColor.YELLOW + "/nation war status" + ChatColor.GRAY + " — текущие войны");
                p.sendMessage(ChatColor.YELLOW + "/nation war all" + ChatColor.GRAY + " — все активные войны");
                return true;
            }

            String warAction = args[1].toLowerCase();
            WarManager warManager = plugin.getWarManager();

            if (warAction.equals("declare")) {
                if (args.length < 3) {
                    p.sendMessage(ChatColor.RED + "Использование: /nation war declare <нация>");
                    return true;
                }

                String targetNation = args[2].toLowerCase();
                // Validate target nation exists
                if (plugin.getConfig().getConfigurationSection("nations." + targetNation) == null) {
                    p.sendMessage(ChatColor.RED + "Нация '" + targetNation + "' не найдена!");
                    return true;
                }

                warManager.declareWar(p, nation, targetNation);
                return true;
            }

            else if (warAction.equals("peace")) {
                warManager.sueForPeace(p, nation);
                return true;
            }

            else if (warAction.equals("status")) {
                warManager.showWarStatus(p, nation);
                return true;
            }

            else if (warAction.equals("all")) {
                warManager.showAllWars(p);
                return true;
            }

            p.sendMessage(ChatColor.RED + "Неизвестное действие. /nation war");
            return true;
        }

        // Если ни одна команда не подошла — отправляем как nation-чат
        String nation = plugin.getNationManager().getPlayerNation(p);
        if (nation != null) {
            String msg = String.join(" ", args);
            String prefix = plugin.getNationManager().getNationPrefixPublic(nation);
            String nationName = plugin.getNationManager().getNationNamePublic(nation);
            String tag = ChatColor.translateAlternateColorCodes('&', prefix + nationName);
            String formatted = ChatColor.DARK_GRAY + "[" + tag + ChatColor.DARK_GRAY + "] "
                    + ChatColor.WHITE + p.getName() + ChatColor.GRAY + ": "
                    + ChatColor.translateAlternateColorCodes('&', msg);
            for (Player member : Bukkit.getOnlinePlayers()) {
                if (nation.equals(plugin.getNationManager().getPlayerNation(member))) {
                    member.sendMessage(formatted);
                }
            }
            plugin.getLogger().info("[NationChat] " + p.getName() + " [" + nation + "]: " + msg);
            return true;
        }

        p.sendMessage(ChatColor.RED + "Неизвестная команда. /n help");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        String last = args.length > 0 ? args[args.length - 1].toLowerCase() : "";

        if (args.length == 1) {
            List<String> subs = java.util.Arrays.asList(
                "buyclaim", "buy", "feed", "feedclaim", "claim", "unclaim", "autoclaim",
                "sethome", "home", "claims", "list", "tp", "teleport", "change", "reset",
                "festival", "party", "trust", "untrust", "charge",
                "info", "top", "leave", "members", "donate", "war", "admin"
            );
            completions.addAll(subs);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("trust") || sub.equals("untrust")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    completions.add(online.getName());
                }
            } else if (sub.equals("war")) {
                completions.addAll(java.util.Arrays.asList("declare", "peace", "status", "all"));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("war") && args[1].equalsIgnoreCase("declare")) {
                // Add nation names for tab completion
                if (plugin.getConfig().getConfigurationSection("nations") != null) {
                    completions.addAll(plugin.getConfig().getConfigurationSection("nations").getKeys(false));
                }
            }
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    private String formatEffectName(String potionName) {
        if (potionName == null) return "???";
        switch (potionName) {
            case "FAST_DIGGING": return "Спешка";
            case "SPEED": return "Скорость";
            case "INVISIBILITY": return "Невидимость";
            case "REGENERATION": return "Регенерация";
            case "INCREASE_DAMAGE": return "Сила";
            case "DAMAGE_RESISTANCE": return "Сопротивление";
            case "JUMP": return "Прыжки";
            case "NIGHT_VISION": return "Ночное зрение";
            case "WATER_BREATHING": return "Водное дыхание";
            case "LUCK": return "Удача";
            case "SLOW": return "Замедление";
            case "WEAKNESS": return "Слабость";
            case "POISON": return "Отравление";
            case "SATURATION": return "Сытость";
            case "GLOWING": return "Свечение";
            case "HERO_OF_VILLAGE": return "Герой деревни";
            default: return potionName;
        }
    }

    private String toRoman(int num) {
        switch (num) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            default: return String.valueOf(num);
        }
    }

    private int getNationRank(VKChatNationsPlugin plugin, String nation) {
        java.util.List<Integer> banks = new java.util.ArrayList<>();
        for (String n : plugin.getConfig().getConfigurationSection("nations").getKeys(false)) {
            banks.add(plugin.getNationManager().getBank(n));
        }
        banks.sort(java.util.Collections.reverseOrder());
        int myBank = plugin.getNationManager().getBank(nation);
        for (int i = 0; i < banks.size(); i++) {
            if (banks.get(i).equals(myBank)) return i + 1;
        }
        return banks.size();
    }
}
