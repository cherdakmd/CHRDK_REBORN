package ru.example.vkchatjobs;

import ru.example.vkchat.api.VKCommandEvent;
import ru.example.vkchat.VKChatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class JobsListener implements Listener {
    private final VKChatJobsPlugin plugin;
    private final ru.example.vkchatjobs.resolver.MaterialResolver materialResolver;

    public JobsListener(VKChatJobsPlugin plugin) {
        this.plugin = plugin;
        this.materialResolver = new ru.example.vkchatjobs.resolver.MaterialResolver(plugin);
    }

    public ru.example.vkchatjobs.resolver.MaterialResolver getMaterialResolver() {
        return materialResolver;
    }

    @EventHandler
    public void onVKCommand(VKCommandEvent e) {
        if (e.getCommand().equals("!работы") || e.getCommand().equals("!jobs")) {
            e.setCancelled(true);
            UUID targetUuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(e.getSenderVkId());
            if (targetUuid == null) {
                VKChatPlugin.getInstance().getApi().sendMessage(e.getPeerId(), "❌ Твой аккаунт не привязан к серверу Minecraft!");
                return;
            }

            int m = plugin.getJobsDataManager().getLevel(targetUuid, "miner");
            int w = plugin.getJobsDataManager().getLevel(targetUuid, "woodcutter");
            int f = plugin.getJobsDataManager().getLevel(targetUuid, "farmer");
            int a = plugin.getJobsDataManager().getLevel(targetUuid, "alchemist");
            int b = plugin.getJobsDataManager().getLevel(targetUuid, "blacksmith");
            int h = plugin.getJobsDataManager().getLevel(targetUuid, "hunter");
            int fi = plugin.getJobsDataManager().getLevel(targetUuid, "fisherman");
            int fatigue = plugin.getJobsDataManager().getFatigue(targetUuid);
            int maxF = plugin.getConfig().getInt("fatigue.max-fatigue", 1000);

            String msg = " Твои уровни профессий:\n\n" +
                    "⛏ Шахтер: " + m + " ур.\n" +
                    " Лесоруб: " + w + " ур.\n" +
                    " Фермер: " + f + " ур.\n" +
                    " Алхимик: " + a + " ур.\n" +
                    "⚒ Кузнец: " + b + " ур.\n" +
                    " Охотник: " + h + " ур.\n" +
                    " Рыбак: " + fi + " ур.\n\n" +
                    " Усталость: " + fatigue + " / " + maxF;

            VKChatPlugin.getInstance().getApi().sendMessage(e.getPeerId(), msg);
        }
    }

    private boolean checkFatigue(Player p, String job) {
        if (!plugin.getConfig().getBoolean("fatigue.enabled", true)) return true;

        int fatigue = plugin.getJobsDataManager().getFatigue(p.getUniqueId());
        int max = plugin.getConfig().getInt("fatigue.max-fatigue", 1000);

        if (fatigue >= max) {
            p.sendTitle(org.bukkit.ChatColor.RED + "Вы устали!", org.bukkit.ChatColor.GRAY + "Отдохните немного...", 10, 40, 10);
            return false;
        }
        int perAction = plugin.getConfig().getInt("fatigue.fatigue-per-action", 3);
        perAction = plugin.getJobsDataManager().applyFatigueModifier(p.getUniqueId(), job, perAction);
        plugin.getJobsDataManager().addFatigue(p.getUniqueId(), perAction);
        return true;
    }

    private void notifyXpGain(Player p, String job, int xpGained) {
        if (!plugin.getConfig().getBoolean("settings.xp-feedback", true)) return;
        int lvl = plugin.getJobsDataManager().getLevel(p.getUniqueId(), job);
        int exp = plugin.getJobsDataManager().getExp(p.getUniqueId(), job);
        int req = Math.max(1, lvl * 1000);
        String jobEmoji = getJobEmoji(job);
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            new net.md_5.bungee.api.chat.TextComponent(
                org.bukkit.ChatColor.AQUA + jobEmoji + " +" + xpGained + " XP | " +
                org.bukkit.ChatColor.GREEN + exp + "/" + req + " XP"
            ));
    }

    private String getJobEmoji(String job) {
        switch (job) {
            case "miner": return "⛏";
            case "woodcutter": return "🌲";
            case "farmer": return "🌾";
            case "alchemist": return "⚗";
            case "blacksmith": return "⚒";
            case "hunter": return "🏹";
            case "fisherman": return "🎣";
            default: return "⭐";
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        Material m = e.getBlock().getType();

        if (materialResolver.isOre(m)) {
            if (plugin.getPlacedBlockTracker() != null && plugin.getPlacedBlockTracker().consumeIfPlaced(e.getBlock())) return;
            if (checkFatigue(p, "miner")) {
                plugin.getJobsDataManager().addExp(p, "miner", 50);
                notifyXpGain(p, "miner", 50);
                plugin.getJobsDataManager().addDailyProgress(p, "miner", 1);
                plugin.getJobsDataManager().addActionReputation(p, "miner", m.name(), 1);
                if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "mine", 1);
                if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);
                Block b = e.getBlock();
                ItemStack tool = p.getInventory().getItemInMainHand();

                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "miner", "miner_double")) {
                    if (Math.random() < 0.1) {
                        // Дополнительный дроп — создаём отдельно, не через getDrops()
                        ItemStack bonus = new ItemStack(m, 1);
                        b.getWorld().dropItemNaturally(b.getLocation(), bonus);
                        p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Сработал навык Удачливый шахтер!");
                    }
                }

                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "miner", "miner_magnet")) {
                    if (Math.random() < 0.15) {
                        ItemStack ingot = materialResolver.getIngotFromOre(m);
                        if (ingot != null) {
                            b.getWorld().dropItemNaturally(b.getLocation(), ingot);
                            p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Магнит Руд притянул слиток!");
                        }
                    }
                }

                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "miner", "miner_blast")) {
                    if (Math.random() < 0.05) {
                        b.getWorld().createExplosion(b.getLocation(), 0.0f, false, false);
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    if (dx == 0 && dy == 0 && dz == 0) continue;
                                    Block rel = b.getRelative(dx, dy, dz);
                                    if (materialResolver.isOre(rel.getType())) {
                                        for (ItemStack drop : rel.getDrops(tool)) {
                                            b.getWorld().dropItemNaturally(rel.getLocation(), drop);
                                        }
                                        rel.setType(Material.AIR);
                                    }
                                }
                            }
                        }
                        p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Взрывное дело! Соседние руды разлетелись!");
                    }
                }

                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "miner", "miner_vein")) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (dx == 0 && dy == 0 && dz == 0) continue;
                                Block rel = b.getRelative(dx, dy, dz);
                                if (rel.getType() == m) {
                                    for (ItemStack drop : rel.getDrops(tool)) {
                                        b.getWorld().dropItemNaturally(rel.getLocation(), drop);
                                    }
                                    rel.setType(Material.AIR);
                                }
                            }
                        }
                    }
                }

                minerQuest(p);
            }
        } else if (materialResolver.isLog(m)) {
            if (plugin.getPlacedBlockTracker() != null && plugin.getPlacedBlockTracker().consumeIfPlaced(e.getBlock())) return;
            if (checkFatigue(p, "woodcutter")) {
                plugin.getJobsDataManager().addExp(p, "woodcutter", 20);
                notifyXpGain(p, "woodcutter", 20);
                plugin.getJobsDataManager().addDailyProgress(p, "woodcutter", 1);
                plugin.getJobsDataManager().addActionReputation(p, "woodcutter", m.name(), 1);
                if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "mine", 1);
                if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);
                Block b = e.getBlock();

                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "woodcutter", "wood_double")) {
                    if (Math.random() < 0.1) {
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(m));
                        p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Сработал навык Двойной дроп!");
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "woodcutter", "wood_apple")) {
                    if (Math.random() < 0.05) {
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.APPLE));
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "woodcutter", "wood_cap") ||
                        plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "woodcutter", "wood_lumberjack")) {
                    int height = plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "woodcutter", "wood_lumberjack") ? 8 : 5;
                    for (int i = 1; i <= height; i++) {
                        Block up = b.getRelative(0, i, 0);
                        if (up.getType() == m) {
                            for (ItemStack drop : up.getDrops(p.getInventory().getItemInMainHand())) {
                                b.getWorld().dropItemNaturally(up.getLocation(), drop);
                            }
                            up.setType(Material.AIR);
                        } else break;
                    }
                }

                woodcutterQuest(p);
            }
        } else if (materialResolver.isCrop(m)) {
            if (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Ageable) {
                org.bukkit.block.data.Ageable age = (org.bukkit.block.data.Ageable) e.getBlock().getBlockData();
                if (age.getAge() < age.getMaximumAge()) return;
            }
            if (checkFatigue(p, "farmer")) {
                plugin.getJobsDataManager().addExp(p, "farmer", 15);
                notifyXpGain(p, "farmer", 15);
                plugin.getJobsDataManager().addDailyProgress(p, "farmer", 1);
                plugin.getJobsDataManager().addActionReputation(p, "farmer", m.name(), 1);
                if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "build", 1);
                if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);
                Block b = e.getBlock();

                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "farmer", "farm_double")) {
                    if (Math.random() < 0.1) {
                        ItemStack bonus = new ItemStack(m, 1);
                        b.getWorld().dropItemNaturally(b.getLocation(), bonus);
                        p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Сработал навык Щедрый урожай!");
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "farmer", "farm_auto")) {
                    Material seed = materialResolver.getSeedFromCrop(m);
                    if (seed != null) {
                        ItemStack seedStack = new ItemStack(seed);
                        if (p.getInventory().containsAtLeast(seedStack, 1)) {
                            p.getInventory().removeItem(seedStack);
                            Bukkit.getScheduler().runTask(plugin, () -> b.setType(m));
                        }
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "farmer", "farm_gold")) {
                    if (Math.random() < 0.01) {
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Math.random() < 0.5 ? Material.GOLDEN_CARROT : Material.GOLDEN_APPLE));
                        p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Золотые руки принесли редкий урожай!");
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "farmer", "farm_master")) {
                    if (Math.random() < 0.005) {
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.DIAMOND));
                        p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Деметра даровала алмаз!");
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "farmer", "farm_bone")) {
                    if (Math.random() < 0.05) {
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(Material.BONE_MEAL));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBrew(BrewEvent e) {
        Player p = null;
        double minDist = Double.MAX_VALUE;
        for (Player pl : e.getBlock().getWorld().getPlayers()) {
            double d = pl.getLocation().distanceSquared(e.getBlock().getLocation());
            if (d < minDist && d < 100) { minDist = d; p = pl; }
        }
        if (p == null) return;
        if (checkFatigue(p, "alchemist")) {
            plugin.getJobsDataManager().addExp(p, "alchemist", 150);
            notifyXpGain(p, "alchemist", 150);
            plugin.getJobsDataManager().addDailyProgress(p, "alchemist", 1);
            plugin.getJobsDataManager().addActionReputation(p, "alchemist", "BREW", 1);
            if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "craft", 1);
            if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "alchemist", "alch_save")) {
                if (Math.random() < 0.1) {
                    p.getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(Material.NETHER_WART));
                    p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Экономия: ингредиент сохранился!");
                }
            }
            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "alchemist", "alch_double")) {
                if (Math.random() < 0.1) {
                    Inventory inv = e.getContents();
                    for (int i = 0; i < inv.getSize(); i++) {
                        ItemStack item = inv.getItem(i);
                        if (item != null && item.getType() != Material.AIR) {
                            ItemStack extra = item.clone();
                            extra.setAmount(1);
                            p.getWorld().dropItemNaturally(e.getBlock().getLocation(), extra);
                        }
                    }
                    p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Дополнительная порция!");
                }
            }
            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "alchemist", "alch_master")) {
                if (Math.random() < 0.01) {
                    p.getWorld().dropItemNaturally(e.getBlock().getLocation(), new ItemStack(Material.POTION));
                    p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Философский камень создал зелье!");
                }
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (e.isCancelled() || !(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack res = e.getCurrentItem();
        if (res == null) return;

        if (materialResolver.isBlacksmithItem(res.getType())) {
            if (checkFatigue(p, "blacksmith")) {
                plugin.getJobsDataManager().addExp(p, "blacksmith", 100);
                notifyXpGain(p, "blacksmith", 100);
                plugin.getJobsDataManager().addDailyProgress(p, "blacksmith", 1);
                if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "craft", 1);
                if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "blacksmith", "black_save")) {
                    if (Math.random() < 0.1) {
                        for (ItemStack item : e.getInventory().getMatrix()) {
                            if (item != null && (item.getType() == Material.IRON_INGOT || item.getType() == Material.DIAMOND || item.getType() == Material.GOLD_INGOT || item.getType() == Material.NETHERITE_INGOT)) {
                                p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(item.getType(), 1));
                                p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Сработал навык Экономия металла!");
                                break;
                            }
                        }
                    }
                }
                if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "blacksmith", "black_master")) {
                    if (Math.random() < 0.05) {
                        p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Кузня Богов: предмет получает +1 к заточке!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        Player p = entity.getKiller();
        if (p == null) return;

        if (checkFatigue(p, "hunter")) {
            plugin.getJobsDataManager().addDailyProgress(p, "hunter", 1);
            if (!isSpawnerMob(entity)) plugin.getJobsDataManager().addActionReputation(p, "hunter", entity.getType().name(), 1);
            if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "kill", 1);
            if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);
            int xp = 30;
            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "hunter", "hunt_exp")) {
                xp = (int) (xp * 1.5);
            }
            plugin.getJobsDataManager().addExp(p, "hunter", xp);
            notifyXpGain(p, "hunter", xp);

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "hunter", "hunt_loot")) {
                if (Math.random() < 0.1) {
                    for (ItemStack drop : e.getDrops()) {
                        if (drop != null) {
                            p.getWorld().dropItemNaturally(entity.getLocation(), drop.clone());
                        }
                    }
                    p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Собиратель: двойной лут с моба!");
                }
            }

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "hunter", "hunt_master")) {
                if (entity.getHealth() <= 0 && Math.random() < 0.1 && !entity.getType().name().contains("BOSS") && !entity.getType().name().contains("ENDER_DRAGON") && !entity.getType().name().contains("WITHER")) {
                    p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Мясник добил жертву!");
                }
            }

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "hunter", "hunt_speed")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, false, false));
            }

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "hunter", "hunt_legend")) {
                if (Math.random() < 0.01) {
                    p.getWorld().dropItemNaturally(entity.getLocation(), new ItemStack(Material.NETHERITE_SCRAP));
                    p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Легенда охоты: добыта редкая награда!");
                }
            }

            hunterQuest(p);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();

        if (checkFatigue(p, "fisherman")) {
            plugin.getJobsDataManager().addDailyProgress(p, "fisherman", 1);
            int xp = 40;
            plugin.getJobsDataManager().addExp(p, "fisherman", xp);
            notifyXpGain(p, "fisherman", xp);
            if (plugin.getWeeklyTaskManager() != null) plugin.getWeeklyTaskManager().addProgress(p, "fish", 1);
            if (plugin.getRankingManager() != null) plugin.getRankingManager().addWeeklyRep(p.getUniqueId(), 1);
            String caughtKey = "FISH";
            if (e.getCaught() instanceof org.bukkit.entity.Item) {
                ItemStack caughtStack = ((org.bukkit.entity.Item)e.getCaught()).getItemStack();
                if (caughtStack != null) caughtKey = caughtStack.getType().name();
            }
            plugin.getJobsDataManager().addActionReputation(p, "fisherman", caughtKey, 1);

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "fisherman", "fish_double")) {
                if (Math.random() < 0.1 && e.getCaught() != null && e.getCaught() instanceof org.bukkit.entity.Item) {
                    org.bukkit.entity.Item caught = (org.bukkit.entity.Item) e.getCaught();
                    ItemStack item = caught.getItemStack();
                    if (item != null) {
                        p.getWorld().dropItemNaturally(caught.getLocation(), item.clone());
                    }
                    p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Двойной улов!");
                }
            }

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "fisherman", "fish_treasure")) {
                if (Math.random() < 0.05) {
                    p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.CHEST));
                    p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Охотник за сокровищами нашел сундук!");
                }
            }

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "fisherman", "fish_catch")) {
                if (Math.random() < 0.05) {
                    Material[] goodies = {Material.EXPERIENCE_BOTTLE, Material.BOOK, Material.IRON_INGOT, Material.GOLD_NUGGET};
                    p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(goodies[(int) (Math.random() * goodies.length)]));
                    p.sendMessage(org.bukkit.ChatColor.AQUA + "✨ Ловец вытащил что-то полезное!");
                }
            }

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), "fisherman", "fish_god")) {
                if (Math.random() < 0.01) {
                    p.getWorld().dropItemNaturally(p.getLocation(), new ItemStack(Material.NETHERITE_SCRAP));
                    p.sendMessage(org.bukkit.ChatColor.GOLD + "✨ Бог рыбалки: добыта древняя награда!");
                }
            }

            fishermanQuest(p);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (title.equals("§8▸ §e§lПРОФЕССИИ §8◂ §7Меню")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            Player p = (Player) e.getWhoClicked();
            Material t = e.getCurrentItem().getType();
            if (t == Material.IRON_PICKAXE) plugin.getSkillManager().openSkillMenu(p, "miner");
            else if (t == Material.IRON_AXE) plugin.getSkillManager().openSkillMenu(p, "woodcutter");
            else if (t == Material.IRON_HOE) plugin.getSkillManager().openSkillMenu(p, "farmer");
            else if (t == Material.BREWING_STAND) plugin.getSkillManager().openSkillMenu(p, "alchemist");
            else if (t == Material.ANVIL) plugin.getSkillManager().openSkillMenu(p, "blacksmith");
            else if (t == Material.BONE) plugin.getSkillManager().openSkillMenu(p, "hunter");
            else if (t == Material.FISHING_ROD) plugin.getSkillManager().openSkillMenu(p, "fisherman");
            else if (t == Material.WRITABLE_BOOK) openDailyMenu(p);
        } else if (title.equals(org.bukkit.ChatColor.AQUA + "Ежедневные профессии")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            Player p = (Player) e.getWhoClicked();
            String job = jobFromMaterial(e.getCurrentItem().getType());
            if (job != null) {
                if (!plugin.getJobsDataManager().claimDaily(p, job)) {
                    p.sendMessage(org.bukkit.ChatColor.RED + "Ежедневка ещё не выполнена или уже получена.");
                }
                openDailyMenu(p);
            }
        } else if (title.startsWith(org.bukkit.ChatColor.DARK_GREEN + "Навыки: ")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;

            Player p = (Player) e.getWhoClicked();
            String job = title.substring(title.indexOf(": ") + 2);
            ItemStack item = e.getCurrentItem();
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;

            NamespacedKey skillKey = new NamespacedKey(plugin, "skill_id");
            if (!meta.getPersistentDataContainer().has(skillKey, PersistentDataType.STRING)) return;
            String skillId = meta.getPersistentDataContainer().get(skillKey, PersistentDataType.STRING);

            SkillManager.SkillDef sd = null;
            for (SkillManager.SkillDef def : plugin.getSkillManager().getSkillsForJob(job)) {
                if (def.id.equals(skillId)) {
                    sd = def;
                    break;
                }
            }
            if (sd == null) return;

            if (plugin.getJobsDataManager().hasSkill(p.getUniqueId(), job, sd.id)) {
                p.sendMessage(org.bukkit.ChatColor.YELLOW + "Этот навык уже изучен.");
                return;
            }

            if (sd.reqSkill != null && !plugin.getJobsDataManager().hasSkill(p.getUniqueId(), job, sd.reqSkill)) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Сначала изучите предыдущий навык!");
                return;
            }

            int lvl = plugin.getJobsDataManager().getLevel(p.getUniqueId(), job);
            if (lvl < sd.reqLevel) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Недостаточно высокий уровень профессии!");
                return;
            }

            int pts = plugin.getJobsDataManager().getSkillPoints(p.getUniqueId(), job);
            if (pts <= 0) {
                p.sendMessage(org.bukkit.ChatColor.RED + "❌ Недостаточно очков навыков!");
                return;
            }

            plugin.getJobsDataManager().removeSkillPoint(p.getUniqueId(), job);
            plugin.getJobsDataManager().unlockSkill(p.getUniqueId(), job, sd.id);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
            p.sendMessage(org.bukkit.ChatColor.GREEN + "Вы изучили навык: " + sd.name + "!");
            plugin.getSkillManager().openSkillMenu(p, job);
        }
    }


    private void openDailyMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, org.bukkit.ChatColor.AQUA + "Ежедневные профессии");
        String[] jobs = {"miner", "woodcutter", "farmer", "alchemist", "blacksmith", "hunter", "fisherman"};
        Material[] mats = {Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_HOE, Material.BREWING_STAND, Material.ANVIL, Material.BONE, Material.FISHING_ROD};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < jobs.length; i++) {
            String job = jobs[i];
            int prog = plugin.getJobsDataManager().getDailyProgress(p.getUniqueId(), job);
            int target = plugin.getJobsDataManager().getDailyTarget(job);
            boolean claimed = plugin.getJobsDataManager().isDailyClaimed(p.getUniqueId(), job);
            ItemStack item = new ItemStack(mats[i]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(org.bukkit.ChatColor.GREEN + job + " " + prog + "/" + target);
            meta.setLore(java.util.Arrays.asList(
                    org.bukkit.ChatColor.GRAY + "Выполняй действия профессии сегодня.",
                    org.bukkit.ChatColor.GRAY + "Награда: " + plugin.getJobsDataManager().getDailyRewardRep(p.getUniqueId(), job) + " реп. + ванильный предмет",
                    claimed ? org.bukkit.ChatColor.GREEN + "✅ Получено" : (prog >= target ? org.bukkit.ChatColor.YELLOW + "▶ Нажми, чтобы забрать" : org.bukkit.ChatColor.RED + "⏳ В процессе")
            ));
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }
        p.openInventory(inv);
    }

    private String jobFromMaterial(Material t) {
        if (t == Material.IRON_PICKAXE) return "miner";
        if (t == Material.IRON_AXE) return "woodcutter";
        if (t == Material.IRON_HOE) return "farmer";
        if (t == Material.BREWING_STAND) return "alchemist";
        if (t == Material.ANVIL) return "blacksmith";
        if (t == Material.BONE) return "hunter";
        if (t == Material.FISHING_ROD) return "fisherman";
        return null;
    }


    private boolean isSpawnerMob(org.bukkit.entity.Entity entity) {
        try {
            org.bukkit.plugin.Plugin mobs = org.bukkit.Bukkit.getPluginManager().getPlugin("VKChatMobs");
            if (mobs != null && entity.getPersistentDataContainer().has(new NamespacedKey(mobs, "from_spawner"), PersistentDataType.INTEGER)) return true;
            if (entity.getPersistentDataContainer().has(new NamespacedKey(plugin, "from_spawner"), PersistentDataType.INTEGER)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // isOre/isCrop/getIngotFromOre/getSeedFromCrop — вынесены в MaterialResolver

    private void minerQuest(Player p) {
        NamespacedKey compKey = new NamespacedKey(plugin, "jobs_task_miner_completed");
        NamespacedKey progKey = new NamespacedKey(plugin, "jobs_task_miner_progress");
        if (!p.getPersistentDataContainer().has(compKey, PersistentDataType.INTEGER)) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 100) {
                p.getPersistentDataContainer().set(compKey, PersistentDataType.INTEGER, 1);
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 100);
                rewardVKRep(p, 150);
                p.sendTitle("§a⛏️ Квест Выполнен!", "§6+150 репутации ВК за шахтера!", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
                if (prog % 10 == 0) {
                    p.sendMessage("§a⛏️ [Ежедневный квест] Добыто руды: " + prog + "/100");
                }
            }
        }
    }

    private void woodcutterQuest(Player p) {
        NamespacedKey compKey = new NamespacedKey(plugin, "jobs_task_woodcutter_completed");
        NamespacedKey progKey = new NamespacedKey(plugin, "jobs_task_woodcutter_progress");
        if (!p.getPersistentDataContainer().has(compKey, PersistentDataType.INTEGER)) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 100) {
                p.getPersistentDataContainer().set(compKey, PersistentDataType.INTEGER, 1);
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 100);
                rewardVKRep(p, 150);
                p.sendTitle("§a🌲 Квест Выполнен!", "§6+150 репутации ВК за лесоруба!", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
                if (prog % 10 == 0) {
                    p.sendMessage("§a🌲 [Ежедневный квест] Срублено дерева: " + prog + "/100");
                }
            }
        }
    }

    private void hunterQuest(Player p) {
        NamespacedKey compKey = new NamespacedKey(plugin, "jobs_task_hunter_completed");
        NamespacedKey progKey = new NamespacedKey(plugin, "jobs_task_hunter_progress");
        if (!p.getPersistentDataContainer().has(compKey, PersistentDataType.INTEGER)) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 100) {
                p.getPersistentDataContainer().set(compKey, PersistentDataType.INTEGER, 1);
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 100);
                rewardVKRep(p, 150);
                p.sendTitle("§a🏹 Квест Выполнен!", "§6+150 репутации ВК за охотника!", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
                if (prog % 10 == 0) {
                    p.sendMessage("§a🏹 [Ежедневный квест] Убито мобов: " + prog + "/100");
                }
            }
        }
    }

    private void fishermanQuest(Player p) {
        NamespacedKey compKey = new NamespacedKey(plugin, "jobs_task_fisherman_completed");
        NamespacedKey progKey = new NamespacedKey(plugin, "jobs_task_fisherman_progress");
        if (!p.getPersistentDataContainer().has(compKey, PersistentDataType.INTEGER)) {
            int prog = p.getPersistentDataContainer().getOrDefault(progKey, PersistentDataType.INTEGER, 0) + 1;
            if (prog >= 100) {
                p.getPersistentDataContainer().set(compKey, PersistentDataType.INTEGER, 1);
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, 100);
                rewardVKRep(p, 150);
                p.sendTitle("§a🎣 Квест Выполнен!", "§6+150 репутации ВК за рыбака!", 10, 70, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                p.getPersistentDataContainer().set(progKey, PersistentDataType.INTEGER, prog);
                if (prog % 10 == 0) {
                    p.sendMessage("§a🎣 [Ежедневный квест] Поймано рыбы: " + prog + "/100");
                }
            }
        }
    }

    private void rewardVKRep(Player p, int amount) {
        try {
            int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getApi().addReputation(vkId, amount);
            }
        } catch (Exception ignored) {}
    }
}
