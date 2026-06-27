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
import ru.example.vkchat.VKChatPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class NationCommand implements CommandExecutor, TabCompleter {
    private final VKChatNationsPlugin plugin;
    private final java.util.Map<Player, Long> tpCooldowns = new java.util.HashMap<>();

    public NationCommand(VKChatNationsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (args.length == 0) {
            plugin.getGuiListener().openGui(p);
            return true;
        }

        String action = args[0].toLowerCase();

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
            
            long last = tpCooldowns.getOrDefault(p, 0L);
            if (System.currentTimeMillis() - last < 300000) {
                long left = (300000 - (System.currentTimeMillis() - last)) / 1000;
                p.sendMessage(ChatColor.RED + "Телепортация на кулдауне! Осталось: " + left + " сек.");
                return true;
            }

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "Сначала привяжите ВКонтакте (/vklink), чтобы телепортироваться за репутацию!");
                return true;
            }

            int cost = 20;
            if (VKChatPlugin.getInstance().getApi().getReputation(vkId) < cost) {
                p.sendMessage(ChatColor.RED + "Недостаточно репутации ВК! Требуется: " + cost);
                return true;
            }

            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            tpCooldowns.put(p, System.currentTimeMillis());
            
            org.bukkit.Location target = new org.bukkit.Location(p.getWorld(), claim.getHomeX(), claim.getHomeY(), claim.getHomeZ(), p.getLocation().getYaw(), p.getLocation().getPitch());
            p.teleport(target);
            p.sendMessage(ChatColor.GREEN + "✓ Вы телепортировались на точку возрождения привата! Списано 20 репутации.");
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
            long last = tpCooldowns.getOrDefault(p, 0L);
            if (System.currentTimeMillis() - last < 300000) {
                long left = (300000 - (System.currentTimeMillis() - last)) / 1000;
                p.sendMessage(ChatColor.RED + "⏳ Телепортация на перезарядке! Подождите " + left + " сек.");
                return true;
            }

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте! (/vklink)");
                return true;
            }

            int cost = 20;
            if (VKChatPlugin.getInstance().getApi().getReputation(vkId) < cost) {
                p.sendMessage(ChatColor.RED + "❌ Недостаточно репутации ВК! Требуется: " + cost + " реп.");
                return true;
            }

            // Списываем репутацию и обновляем кулдаун
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);
            tpCooldowns.put(p, System.currentTimeMillis());

            // Вычисляем безопасную точку приземления над блоком привата
            org.bukkit.Location target = new org.bukkit.Location(p.getWorld(), blockX + 0.5, blockY + 1.0, blockZ + 0.5, p.getLocation().getYaw(), p.getLocation().getPitch());

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

            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "❌ Сначала привяжите ВКонтакте (/vklink), чтобы изменить Нацию!");
                return true;
            }

            int cost = 5000;
            int rep = VKChatPlugin.getInstance().getApi().getReputation(vkId);
            if (rep < cost) {
                p.sendMessage(ChatColor.RED + "❌ Смена нации/расы — это величайший ритуал, который стоит " + ChatColor.GOLD + cost + ChatColor.RED + " реп. ВК!");
                p.sendMessage(ChatColor.GRAY + "Ваш баланс: " + ChatColor.YELLOW + rep + ChatColor.GRAY + " реп. ВК.");
                return true;
            }

            // Списываем репутацию
            VKChatPlugin.getInstance().getApi().takeReputation(vkId, cost);

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
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
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
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId == -1) {
                p.sendMessage(ChatColor.RED + "Сначала привяжи ВКонтакте (/vklink)!");
                return true;
            }
            plugin.getNationManager().chargeWithReputation(p, vkId);
        }

        else if (action.equals("defend") || action.equals("defense")) {
            plugin.getClaimDefenseManager().startManualDefense(p);
        }

        else if (action.equals("info")) {
            String nation = plugin.getNationManager().getPlayerNation(p);
            if (nation == null) {
                p.sendMessage(ChatColor.RED + "Вы не в нации!");
                return true;
            }
            String nationName = plugin.getNationManager().getNationNamePublic(nation);
            int bank = plugin.getNationManager().getBank(nation);
            int claimCount = 0;
            int memberCount = 0;
            for (java.util.Map.Entry<String, ru.example.vkchatnations.data.ChunkClaim> entry : plugin.getNationManager().getNationClaims().entrySet()) {
                if (entry.getValue().getNation().equals(nation)) claimCount++;
            }
            for (java.util.Map.Entry<UUID, String> entry : plugin.getNationManager().getPlayerNations().entrySet()) {
                if (entry.getValue().equals(nation)) memberCount++;
            }
            p.sendMessage(ChatColor.GOLD + "=== " + nationName + " ===");
            p.sendMessage(ChatColor.YELLOW + "Участники: " + ChatColor.WHITE + memberCount);
            p.sendMessage(ChatColor.YELLOW + "Приваты: " + ChatColor.WHITE + claimCount);
            p.sendMessage(ChatColor.YELLOW + "Казна: " + ChatColor.WHITE + bank + " реп.");
            p.sendMessage(ChatColor.YELLOW + "Рейтинг: " + ChatColor.WHITE + getNationRank(plugin, nation));
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
        }

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
                "festival", "party", "trust", "untrust", "charge", "defend", "defense",
                "info", "top", "leave"
            );
            completions.addAll(subs);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("trust") || sub.equals("untrust")) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    completions.add(online.getName());
                }
            }
        }

        return completions.stream().filter(s -> last.isEmpty() || s.toLowerCase().startsWith(last)).collect(Collectors.toList());
    }

    private int getNationRank(VKChatNationsPlugin plugin, String nation) {
        java.util.List<Integer> banks = new java.util.ArrayList<>();
        for (String n : plugin.getConfig().getConfigurationSection("nations").getKeys(false)) {
            banks.add(plugin.getNationManager().getBank(n));
        }
        banks.sort(java.util.Collections.reverseOrder());
        int myBank = plugin.getNationManager().getBank(nation);
        for (int i = 0; i < banks.size(); i++) {
            if (banks.get(i) == myBank) return i + 1;
        }
        return banks.size();
    }
}
