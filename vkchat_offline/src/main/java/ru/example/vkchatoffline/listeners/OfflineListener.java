package ru.example.vkchatoffline.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.api.VKMessageEvent;
import ru.example.vkchatoffline.VKChatOfflinePlugin;
import ru.example.vkchatoffline.data.Expedition;
import ru.example.vkchatoffline.managers.AdventureCommandManager;
import ru.example.vkchatoffline.managers.ShiftManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Обработчик VK сообщений и событий оффлайн-походов.
 * Интегрирован с новой рогалик-системой событий.
 */
public class OfflineListener implements Listener {
    private final VKChatOfflinePlugin plugin;

    public OfflineListener(VKChatOfflinePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Перезагрузка после изменения конфига.
     */
    public void reload() {
        // Менеджер походов пересоздаётся в VKChatOfflinePlugin.reloadConfig()
    }

    private AdventureCommandManager getAdventureManager() {
        return plugin.getAdventureCommandManager();
    }

    @EventHandler
    public void onVKMessage(VKMessageEvent e) {
        if (!plugin.getConfig().getBoolean("enabled", true)) return;

        int peer = e.getPeer();
        int sender = e.getSenderId();
        String msg = e.getMessage().toLowerCase().trim();
        String[] args = msg.split(" ");

        // Команда смены
        if (msg.startsWith("!смена")) {
            handleShiftCommand(peer, sender, args);
            return;
        }

        // Команда крафта
        if (msg.startsWith("!крафт")) {
            handleCraftCommand(peer, sender, args);
            return;
        }

        // Команда спасения
        if (msg.startsWith("!спасти")) {
            getAdventureManager().handleRescueCommand(peer, sender, args);
            return;
        }

        // Команда питомца
        if (msg.startsWith("!питомец")) {
            getAdventureManager().handlePetCommand(peer, sender, args);
            return;
        }

        // Команда похода
        if (msg.equals("!поход")) {
            getAdventureManager().handleExpeditionCommand(peer, sender, args);
            return;
        }

        Expedition exp = getAdventureManager().getExpedition(sender);

        // Обработка ответа на загадку
        if (exp != null && exp.isWaitingRiddle()) {
            String cleanMsg = e.getMessage().trim();
            if (cleanMsg.equalsIgnoreCase("!подсказка")) {
                getAdventureManager().handleRiddleHintRequest(sender);
            } else {
                getAdventureManager().handleRiddleAnswer(sender, cleanMsg);
            }
            return;
        }

        // Обработка выбора локации (только меню)
        if (exp != null && exp.isWaitingChoice() && "menu".equals(exp.getDungeonType())) {
            Integer choice = parseChoice(msg);
            int maxChoice = getAdventureManager().getMenuMaxChoice(exp.getPlayerUuid());
            if (choice != null && choice >= 1 && choice <= maxChoice) {
                getAdventureManager().handleLocationChoice(sender, choice);
            } else if (choice != null) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                        "❌ Выбери локацию: напиши число от 1 до " + maxChoice + ".");
            }
            return;
        }

        // Обработка выбора действия на этапе похода
        if (exp != null && exp.isWaitingChoice() && exp.getEndTime() == 0 && !"menu".equals(exp.getDungeonType())) {
            Integer choice = parseChoice(msg);
            if (choice != null) {
                getAdventureManager().handleActionChoice(sender, choice);
            }
            return;
        }
    }

    private Integer parseChoice(String msg) {
        if (msg == null || msg.isEmpty()) {
            return null;
        }
        String token = msg.trim().split("\\s+")[0].replaceAll("[^0-9]", "");
        if (token.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Обработка команды !смена.
     */
    private void handleShiftCommand(int peer, int sender, String[] args) {
        ShiftManager shiftManager = plugin.getShiftManager();
        if (shiftManager == null) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Система смен временно недоступна.");
            return;
        }

        UUID pUuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(sender);
        if (pUuid == null) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Твой аккаунт не привязан к серверу Minecraft!");
            return;
        }

        // Проверка, есть ли уже активная смена
        if (shiftManager.isWorking(sender)) {
            long left = (shiftManager.getEndTime(sender) - System.currentTimeMillis()) / 1000L;
            if (left > 0) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                        "⏳ Твой персонаж на смене (" + shiftManager.getShiftType(sender) + "). " +
                        "Осталось: " + (left / 3600) + " ч. " + ((left % 3600) / 60) + " мин.");
            } else {
                shiftManager.finishShift(sender);
            }
            return;
        }

        // Проверка на поход
        if (getAdventureManager().isInExpedition(sender)) {
            Expedition exp = getAdventureManager().getExpedition(sender);
            if (exp.getEndTime() == 0) {
                VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                        "❌ Ты уже находишься в походе! Сначала дождись его завершения.");
                return;
            }
            long left = (exp.getEndTime() - System.currentTimeMillis()) / 1000L;
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    " Твой персонаж восстанавливается в лазарете.\nОсталось: " + (left / 3600) + " ч.");
            return;
        }

        // Обработка команды
        if (args.length < 3) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "⛏️ Использование: !смена <шахта/лесопилка/ферма> <часы (1-12)>");
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "Доступные смены:\n" +
                    " • шахта - руды и драгоценные камни\n" +
                    " • лесопилка - древесина и ягоды\n" +
                    " • ферма - зерно и овощи");
            return;
        }

        shiftManager.handleCommand(peer, sender, pUuid, args);
    }

    private void handleCraftCommand(int peer, int sender, String[] args) {
        UUID pUuid = VKChatPlugin.getInstance().getApi().getUuidByVkId(sender);
        if (pUuid == null) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Твой аккаунт не привязан к серверу Minecraft!");
            return;
        }

        if (args.length < 2) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "⚒️ [ОФЛАЙН КРАФТ] Доступные рецепты (ресурсы берутся из /stash):\n" +
                    " • !крафт незерит - Сплавить 1 Незерит (нужно: 4 лома + 4 золотых слитка)\n" +
                    " • !крафт алмаз_блок - Скрафтить 1 Алмазный блок (нужно: 9 алмазов)\n" +
                    " • !крафт железо - Переплавить 1 железную руду в слиток\n" +
                    " • !крафт золото - Переплавить 1 золотую руду в слиток");
            return;
        }

        String recipe = args[1].toLowerCase();
        List<ItemStack> stash = plugin.getStashManager().getItems(pUuid);

        Material req1 = null, req2 = null;
        int reqAmt1 = 0, reqLvlAmt2 = 0;
        ItemStack result = null;

        if (recipe.equals("незерит")) {
            req1 = Material.NETHERITE_SCRAP; reqAmt1 = 4;
            req2 = Material.GOLD_INGOT; reqLvlAmt2 = 4;
            result = new ItemStack(Material.NETHERITE_INGOT, 1);
        } else if (recipe.equals("алмаз_блок")) {
            req1 = Material.DIAMOND; reqAmt1 = 9;
            result = new ItemStack(Material.DIAMOND_BLOCK, 1);
        } else if (recipe.equals("железо")) {
            req1 = Material.IRON_ORE; reqAmt1 = 1;
            result = new ItemStack(Material.IRON_INGOT, 1);
        } else if (recipe.equals("золото")) {
            req1 = Material.GOLD_ORE; reqAmt1 = 1;
            result = new ItemStack(Material.GOLD_INGOT, 1);
        } else {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Неизвестный рецепт! Доступные: незерит, алмаз_блок, железо, золото.");
            return;
        }

        // Проверяем наличие ресурсов в тайнике
        int has1 = 0;
        int has2 = 0;
        for (ItemStack item : stash) {
            if (item != null) {
                if (item.getType() == req1) has1 += item.getAmount();
                if (req2 != null && item.getType() == req2) has2 += item.getAmount();
            }
        }

        if (has1 < reqAmt1 || (req2 != null && has2 < reqLvlAmt2)) {
            VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                    "❌ Недостаточно ресурсов в вашем тайнике /stash! Требуется: " + 
                    reqAmt1 + "x " + req1.name() + (req2 != null ? " и " + reqLvlAmt2 + "x " + req2.name() : "") + 
                    " (У вас: " + has1 + " и " + has2 + ").");
            return;
        }

        // Списываем ресурсы
        int toTake1 = reqAmt1;
        int toTake2 = reqLvlAmt2;

        for (ItemStack item : stash) {
            if (item != null) {
                if (item.getType() == req1 && toTake1 > 0) {
                    int take = Math.min(toTake1, item.getAmount());
                    item.setAmount(item.getAmount() - take);
                    toTake1 -= take;
                }
                if (req2 != null && item.getType() == req2 && toTake2 > 0) {
                    int take = Math.min(toTake2, item.getAmount());
                    item.setAmount(item.getAmount() - take);
                    toTake2 -= take;
                }
            }
        }

        // Очищаем пустые слоты
        stash.removeIf(item -> item == null || item.getAmount() <= 0);

        // Начисляем результат
        stash.add(result);
        plugin.getStashManager().saveItems(pUuid, stash);

        VKChatPlugin.getInstance().getVkManager().sendMessage(peer, sender,
                "✅ [ОФЛАЙН КРАФТ] Вы успешно изготовили " + result.getAmount() + " шт. " + result.getType().name() + "! Предмет зачислен в ваш /stash!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Показ офлайн-уведомлений
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> notifications = getAdventureManager().getExpeditionStorage().popNotifications(uuid);
            if (!notifications.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§e▬▬▬▬▬▬▬▬ [VKChat Offline Отчет] ▬▬▬▬▬▬▬▬");
                    for (String msg : notifications) {
                        player.sendMessage(msg);
                    }
                    player.sendMessage("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                });
            }
        });

        int vkId = VKChatPlugin.getInstance().getApi().getLinkedVkId(player);
        if (vkId != -1) {
            // Проверка лазарета при входе игрока
            Expedition exp = getAdventureManager().getExpedition(vkId);
            if (exp != null && exp.getEndTime() > 0 && exp.getEndTime() < System.currentTimeMillis()) {
                getAdventureManager().clearHospitalCooldown(vkId);
                VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), vkId,
                        " ✅ Лазарет: твой персонаж восстановился! Можно снова ходить в походы.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        savePlayerGearStats(e.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent e) {
        savePlayerGearStats(e.getPlayer());
    }

    private void savePlayerGearStats(Player player) {
        int[] stats = calculateGearStats(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            getAdventureManager().getExpeditionStorage().savePlayerGear(player.getUniqueId(), stats[0], stats[1]);
        });
    }

    private int[] calculateGearStats(Player player) {
        int defense = 0;
        int damage = 5; // Base punch/hand damage

        // 1. Calculate armor defense
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        if (armor != null) {
            for (ItemStack piece : armor) {
                if (piece == null || piece.getType() == Material.AIR) continue;
                Material type = piece.getType();
                
                // Add base armor values
                int pieceDefense = getArmorPieceDefense(type);
                defense += pieceDefense;
                
                // Enchantments (Protection)
                if (piece.hasItemMeta()) {
                    int protLevel = piece.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL);
                    defense += protLevel; // Add 1 point per Protection level
                }
            }
        }

        // 2. Calculate weapon damage
        ItemStack weapon = inv.getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            Material type = weapon.getType();
            damage = getWeaponDamage(type);
            
            // Sharpness enchantment
            if (weapon.hasItemMeta()) {
                int sharpLevel = weapon.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DAMAGE_ALL);
                damage += sharpLevel; // Add 1 point per Sharpness level
            }
        }

        // 3. Scan for custom artifacts in player's inventory (VKChatArtifacts)
        int artifactDefense = 0;
        int artifactDamage = 0;
        org.bukkit.plugin.Plugin artifactsPlugin = Bukkit.getPluginManager().getPlugin("VKChatArtifacts");
        if (artifactsPlugin != null) {
            org.bukkit.NamespacedKey isArtKey = new org.bukkit.NamespacedKey(artifactsPlugin, "is_artifact");
            org.bukkit.NamespacedKey buffTypeKey = new org.bukkit.NamespacedKey(artifactsPlugin, "buff_type");
            org.bukkit.NamespacedKey buffLevelKey = new org.bukkit.NamespacedKey(artifactsPlugin, "buff_level");
            
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.hasItemMeta()) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    if (pdc.has(isArtKey, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                        Integer isArt = pdc.get(isArtKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                        if (isArt != null && isArt == 1) {
                            String buffType = pdc.get(buffTypeKey, org.bukkit.persistence.PersistentDataType.STRING);
                            Integer buffLvl = pdc.get(buffLevelKey, org.bukkit.persistence.PersistentDataType.INTEGER);
                            int level = buffLvl != null ? buffLvl : 1;
                            
                            if ("STEEL_SKIN".equals(buffType)) {
                                artifactDefense += level * 3; // Steel skin gives major armor!
                            } else if ("HEALTH".equals(buffType) || "MAX_HEALTH_BOOST".equals(buffType)) {
                                artifactDefense += level; // Boosts defense slightly too
                            } else if ("DAMAGE".equals(buffType)) {
                                artifactDamage += level * 2; // Damage buff!
                            } else if ("VAMPIRISM".equals(buffType) || "CRITICAL".equals(buffType)) {
                                artifactDamage += level; // Critical/Vampirism boosts virtual damage slightly
                            }
                        }
                    }
                }
            }
        }
        
        defense += artifactDefense;
        damage += artifactDamage;

        return new int[]{damage, defense};
    }

    private int getArmorPieceDefense(Material material) {
        String name = material.name();
        if (name.contains("LEATHER")) {
            if (name.contains("HELMET") || name.contains("BOOTS")) return 1;
            if (name.contains("LEGGINGS")) return 2;
            if (name.contains("CHESTPLATE")) return 3;
        } else if (name.contains("CHAINMAIL") || name.contains("GOLDEN")) {
            if (name.contains("BOOTS")) return 1;
            if (name.contains("HELMET")) return 2;
            if (name.contains("LEGGINGS")) return 3;
            if (name.contains("CHESTPLATE")) return 5;
        } else if (name.contains("IRON")) {
            if (name.contains("BOOTS") || name.contains("HELMET")) return 2;
            if (name.contains("LEGGINGS")) return 5;
            if (name.contains("CHESTPLATE")) return 6;
        } else if (name.contains("DIAMOND") || name.contains("NETHERITE")) {
            if (name.contains("BOOTS") || name.contains("HELMET")) return 3;
            if (name.contains("LEGGINGS")) return 6;
            if (name.contains("CHESTPLATE")) return 8;
        }
        return 0;
    }

    private int getWeaponDamage(Material material) {
        String name = material.name();
        if (name.contains("WOODEN_SWORD")) return 4;
        if (name.contains("GOLDEN_SWORD")) return 4;
        if (name.contains("STONE_SWORD")) return 5;
        if (name.contains("IRON_SWORD")) return 6;
        if (name.contains("DIAMOND_SWORD")) return 7;
        if (name.contains("NETHERITE_SWORD")) return 8;

        if (name.contains("WOODEN_AXE")) return 7;
        if (name.contains("GOLDEN_AXE")) return 7;
        if (name.contains("STONE_AXE")) return 9;
        if (name.contains("IRON_AXE")) return 9;
        if (name.contains("DIAMOND_AXE")) return 9;
        if (name.contains("NETHERITE_AXE")) return 10;
        
        if (name.contains("BOW") || name.contains("CROSSBOW")) return 6;

        return 5; // default or punch
    }

    /**
     * Проверка истекших походов и смен (вызывается таймером).
     */
    public void checkTimers() {
        // Проверка смен
        if (plugin.getShiftManager() != null) {
            plugin.getShiftManager().checkShifts();
        }

        // Проверка походов
        getAdventureManager().checkExpeditions();
    }

    /**
     * Получение всех активных походов.
     */
    public Map<Integer, Expedition> getActiveExpeditions() {
        return getAdventureManager().getActiveExpeditions();
    }

    /**
     * Получение питомцев игроков.
     */
    public Map<Integer, String> getPlayerPets() {
        return getAdventureManager().getPlayerPets();
    }

    /**
     * Улучшенная система лута для походов.
     */
    public void generateLoot(Expedition exp, boolean isBoss) {
        Random random = new Random();

        List<ItemStack> loot = new ArrayList<>();
        int level = exp.getLevel();

        // Базовая добыча по типу локации
        switch (exp.getDungeonType()) {
            case "forest":
                generateForestLoot(loot, level, random);
                break;
            case "mine":
                generateMineLoot(loot, level, random);
                break;
            case "castle":
                generateCastleLoot(loot, level, random, isBoss);
                break;
            case "nether":
                generateNetherLoot(loot, level, random);
                break;
        }

        // --- ДОПОЛНИТЕЛЬНЫЙ ЛЕГЕНДАРНЫЙ ЛУТ ИЗ СЕКРЕТНЫХ СОКРОВИЩНИЦ СИНЕРГИИ СЕТА ---
        if (exp.getPendingEventTitle() != null) {
            String title = exp.getPendingEventTitle();
            if (title.contains("Казна")) {
                loot.add(new ItemStack(Material.GOLD_BLOCK, 1 + random.nextInt(2)));
                loot.add(new ItemStack(Material.DIAMOND, 3 + random.nextInt(4)));
            } else if (title.contains("Лаборатория")) {
                loot.add(new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(2)));
                loot.add(new ItemStack(Material.IRON_BLOCK, 2 + random.nextInt(3)));
            } else if (title.contains("Сокровищница")) {
                loot.add(new ItemStack(Material.EMERALD, 4 + random.nextInt(5)));
                loot.add(new ItemStack(Material.PRISMARINE_SHARD, 2 + random.nextInt(3)));
            }
        }

        // Ночной бонус (100% удваивание по выбору игрока)
        if (exp.isNight()) {
            List<ItemStack> extra = new ArrayList<>(loot);
            loot.addAll(extra);
        }

        // Бонус от серий побед
        if (exp.getConsecutiveWins() >= 5) {
            loot.add(new ItemStack(Material.EMERALD, 1 + random.nextInt(2)));
        }

        exp.addItems(loot);

        // Сообщение о добыче
        if (!loot.isEmpty()) {
            StringBuilder itemsMsg = new StringBuilder("🎒 Твой лут:\n");
            Map<Material, Integer> count = new HashMap<>();
            for (ItemStack item : loot) {
                count.merge(item.getType(), item.getAmount(), Integer::sum);
            }
            for (Map.Entry<Material, Integer> entry : count.entrySet()) {
                itemsMsg.append("   ").append(entry.getKey().name()).append(" x")
                        .append(entry.getValue()).append("\n");
            }
            VKChatPlugin.getInstance().getVkManager().sendMessage(exp.getPeerId(), exp.getSenderId(),
                    itemsMsg.toString());
        }
    }

    private void generateForestLoot(List<ItemStack> loot, int level, Random random) {
        int count = 2 + random.nextInt(3) + level / 3;

        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(100);
            if (roll < 40) {
                loot.add(new ItemStack(Material.APPLE, 1 + random.nextInt(3)));
            } else if (roll < 60) {
                loot.add(new ItemStack(Material.STICK, 3 + random.nextInt(5)));
            } else if (roll < 75) {
                loot.add(new ItemStack(Material.BROWN_MUSHROOM, 2 + random.nextInt(4)));
            } else if (roll < 85) {
                loot.add(new ItemStack(Material.RED_MUSHROOM, 1 + random.nextInt(2)));
            } else if (roll < 92) {
                loot.add(new ItemStack(Material.ROTTEN_FLESH, 1 + random.nextInt(2)));
            } else if (roll < 97) {
                loot.add(new ItemStack(Material.GOLD_NUGGET, 1 + random.nextInt(3)));
            } else {
                loot.add(new ItemStack(Material.MAP, 1));
            }
        }
    }

    private void generateMineLoot(List<ItemStack> loot, int level, Random random) {
        int count = 3 + random.nextInt(4) + level / 2;

        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(100);
            if (roll < 40) {
                loot.add(new ItemStack(Material.COAL, 3 + random.nextInt(8)));
            } else if (roll < 65) {
                loot.add(new ItemStack(Material.IRON_ORE, 1 + random.nextInt(3)));
            } else if (roll < 80) {
                loot.add(new ItemStack(Material.GOLD_ORE, 1 + random.nextInt(2)));
            } else if (roll < 90) {
                loot.add(new ItemStack(Material.LAPIS_LAZULI, 2 + random.nextInt(4)));
            } else if (roll < 95) {
                loot.add(new ItemStack(Material.REDSTONE, 1 + random.nextInt(3)));
            } else if (roll < 98) {
                loot.add(new ItemStack(Material.DIAMOND, 1 + (random.nextInt(2) * level / 5)));
            } else {
                loot.add(new ItemStack(Material.NETHERITE_SCRAP, 1));
            }
        }
    }

    private void generateCastleLoot(List<ItemStack> loot, int level, Random random, boolean isBoss) {
        int count = isBoss ? 8 + random.nextInt(5) : 3 + random.nextInt(4) + level;

        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(100);
            if (roll < 30) {
                loot.add(new ItemStack(Material.GOLD_INGOT, 2 + random.nextInt(5)));
            } else if (roll < 50) {
                loot.add(new ItemStack(Material.DIAMOND, 1 + random.nextInt(3)));
            } else if (roll < 65) {
                loot.add(new ItemStack(Material.EMERALD, 1 + random.nextInt(2)));
            } else if (roll < 75) {
                loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 0 + (random.nextInt(3) * level / 10)));
            } else if (roll < 85) {
                loot.add(new ItemStack(Material.DIAMOND_BLOCK, 0 + (random.nextInt(2) * level / 10)));
            } else if (roll < 93) {
                loot.add(new ItemStack(Material.ENDER_PEARL, 0 + (random.nextInt(2) * level / 10)));
            } else if (roll < 98) {
                loot.add(new ItemStack(Material.GOLD_BLOCK, 1 + (random.nextInt(2) * level / 10)));
            } else {
                loot.add(new ItemStack(Material.DRAGON_HEAD, 1));
            }
        }

        // Эксклюзивный лут для босса
        if (isBoss) {
            if (random.nextInt(100) < 50) {
                ItemStack scroll = new ItemStack(Material.PAPER);
                ItemMeta meta = scroll.getItemMeta();
                meta.setDisplayName(org.bukkit.ChatColor.LIGHT_PURPLE + "Свиток Синтеза");
                scroll.setItemMeta(meta);
                loot.add(scroll);
            }
            if (random.nextInt(100) < 50) {
                ItemStack fragment = new ItemStack(Material.PAPER);
                ItemMeta meta = fragment.getItemMeta();
                meta.setDisplayName(org.bukkit.ChatColor.AQUA + "Фрагмент неизвестных чар");
                fragment.setItemMeta(meta);
                loot.add(fragment);
            }
        }
    }

    private void generateNetherLoot(List<ItemStack> loot, int level, Random random) {
        int count = 4 + random.nextInt(4) + level;
        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(100);
            if (roll < 30) {
                loot.add(new ItemStack(Material.QUARTZ, 3 + random.nextInt(6)));
            } else if (roll < 55) {
                loot.add(new ItemStack(Material.GLOWSTONE_DUST, 2 + random.nextInt(4)));
            } else if (roll < 75) {
                loot.add(new ItemStack(Material.GOLD_NUGGET, 4 + random.nextInt(8)));
            } else if (roll < 88) {
                loot.add(new ItemStack(Material.BLAZE_ROD, 1 + random.nextInt(2)));
            } else if (roll < 96) {
                loot.add(new ItemStack(Material.NETHERITE_SCRAP, 1));
            } else {
                loot.add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
            }
        }
    }
}
