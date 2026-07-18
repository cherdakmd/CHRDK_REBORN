package ru.example.vkchatmobs.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchat.util.JobsBridge;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ContractManager {
    private final VKChatMobsPlugin plugin;

    private final NamespacedKey contractTypeKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey requiredKey;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey completedKey;
    private final NamespacedKey contractElementKey;

    public ContractManager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.contractTypeKey = new NamespacedKey(plugin, "mobs_contract_type");
        this.progressKey = new NamespacedKey(plugin, "mobs_contract_progress");
        this.requiredKey = new NamespacedKey(plugin, "mobs_contract_required");
        this.cooldownKey = new NamespacedKey(plugin, "mobs_contract_last_reset");
        this.completedKey = new NamespacedKey(plugin, "mobs_contracts_completed");
        this.contractElementKey = new NamespacedKey(plugin, "mobs_contract_element");
    }

    public enum ContractType {
        RECRUIT("recruit", "§aОхотник на Новобранцев", "Убить 15 мобов Ранга 3+", 15, 150, 1, 0),
        ELITE("elite", "§dИстребитель Элиты", "Убить 10 мобов Ранга 6+", 10, 250, 2, 0),
        CHAMPION("champion", "§6Гроза Чемпионов", "Убить 3 Мини-Bossов", 3, 350, 1, 1),
        LEGENDARY("legendary", "§c☠ Легендарный Завоеватель ☠", "Убить 1 Мирового Супер-Босса", 1, 500, 3, 2),
        ELEMENTAL("elemental", "§bЭлементальный Охотник", "Убить 20 мобов одного стихийного типа", 20, 300, 1, 1),
        SLAYER("slayer", "§4☠ Истребитель ☠", "Убить 50 мобов любого типа", 50, 400, 3, 2),
        BOSS_HUNTER("boss_hunter", "§c⚔ Охотник на Боссов", "Убить 5 мини-боссов", 5, 500, 4, 3),
        ELEMENT_MASTER("element_master", "§d✦ Мастер Стихий", "Убить 30 мобов одной стихии", 30, 350, 2, 2),
        ARCHETYPE_SLAYER("archetype_slayer", "§5☠ Убийца Архетипов", "Убить 20 мобов одного архетипа", 20, 450, 3, 2),
        STORM_BREAKER("storm_breaker", "§9⚡ Гроза Шторма", "Убить 25 штормовых мобов", 25, 375, 2, 1),
        VOID_CLEANSER("void_cleanser", "§5🌀 Очиститель Бездны", "Убить 10 эндерменов/странников", 10, 320, 1, 2),
        SIEGE_DEFENDER("siege_defender", "§4🛡 Защитник", "Убить 15 осадных мобов", 15, 450, 3, 2);

        private final String id;
        private final String displayName;
        private final String description;
        private final int required;
        private final int repReward;
        private final int runeTokens;
        private final int artifactShards;

        ContractType(String id, String displayName, String description, int required, int repReward, int runeTokens, int artifactShards) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.required = required;
            this.repReward = repReward;
            this.runeTokens = runeTokens;
            this.artifactShards = artifactShards;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getRequired() { return required; }
        public int getRepReward() { return repReward; }
        public int getRuneTokens() { return runeTokens; }
        public int getArtifactShards() { return artifactShards; }

        public static ContractType getById(String id) {
            for (ContractType t : values()) {
                if (t.getId().equalsIgnoreCase(id)) return t;
            }
            return null;
        }
    }

    public boolean hasActiveContract(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        return pdc.has(contractTypeKey, PersistentDataType.STRING);
    }

    public ContractType getActiveContract(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(contractTypeKey, PersistentDataType.STRING)) return null;
        return ContractType.getById(pdc.get(contractTypeKey, PersistentDataType.STRING));
    }

    public int getProgress(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        return pdc.getOrDefault(progressKey, PersistentDataType.INTEGER, 0);
    }

    public long getCooldownRemaining(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        long lastReset = pdc.getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();
        long nextAvailable = lastReset + plugin.getConfig().getLong("contracts.cooldown-hours", 24) * 3600000L;
        return Math.max(0, nextAvailable - now);
    }


    public int getCompletedContracts(Player p) {
        return p.getPersistentDataContainer().getOrDefault(completedKey, PersistentDataType.INTEGER, 0);
    }

    public String getHunterRank(Player p) {
        int c = getCompletedContracts(p);
        if (c >= 50) return "§6Легенда охоты";
        if (c >= 25) return "§cУбийца элиты";
        if (c >= 12) return "§bСледопыт";
        if (c >= 4) return "§aОхотник";
        return "§7Новичок";
    }

    public int getHunterJobLevel(Player p) {
        int level = JobsBridge.getLevel(p, "hunter");
        return level > 0 ? level : 1;
    }

    public void generateContract(Player p) {
        ContractType[] types = ContractType.values();
        int completed = getCompletedContracts(p);
        int hunter = getHunterJobLevel(p);
        List<ContractType> available = new java.util.ArrayList<>(Arrays.asList(ContractType.RECRUIT));
        if (completed >= 3 || hunter >= 10) available.add(ContractType.ELITE);
        if (completed >= 10 || hunter >= 25) available.add(ContractType.CHAMPION);
        if (completed >= 25 || hunter >= 40) available.add(ContractType.LEGENDARY);
        if (completed >= 5 || hunter >= 15) available.add(ContractType.ELEMENTAL);
        if (completed >= 15 || hunter >= 30) available.add(ContractType.SLAYER);
        if (completed >= 20 || hunter >= 35) available.add(ContractType.BOSS_HUNTER);
        if (completed >= 12 || hunter >= 25) available.add(ContractType.ELEMENT_MASTER);
        if (completed >= 28 || hunter >= 42) available.add(ContractType.ARCHETYPE_SLAYER);
        if (completed >= 30 || hunter >= 45) available.add(ContractType.STORM_BREAKER);
        if (completed >= 22 || hunter >= 38) available.add(ContractType.VOID_CLEANSER);
        if (completed >= 18 || hunter >= 30) available.add(ContractType.SIEGE_DEFENDER);
        ContractType selected = available.get(ThreadLocalRandom.current().nextInt(available.size()));

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(contractTypeKey, PersistentDataType.STRING, selected.getId());
        pdc.set(progressKey, PersistentDataType.INTEGER, 0);
        pdc.set(requiredKey, PersistentDataType.INTEGER, selected.getRequired());

        if (selected == ContractType.ELEMENTAL) {
            String[] elements = {"fire", "frost", "poison", "storm", "dark", "light", "void", "nature"};
            String element = elements[ThreadLocalRandom.current().nextInt(elements.length)];
            pdc.set(contractElementKey, PersistentDataType.STRING, element);
            String elementName;
            switch (element) {
                case "fire": elementName = "Огонь"; break;
                case "frost": elementName = "Лёд"; break;
                case "poison": elementName = "Яд"; break;
                case "storm": elementName = "Буря"; break;
                case "dark": elementName = "Тьма"; break;
                case "light": elementName = "Свет"; break;
                case "void": elementName = "Бездна"; break;
                case "nature": elementName = "Природа"; break;
                default: elementName = element;
            }
            p.sendMessage("§fЗадача: §bУбить 20 мобов стихии §" + element.charAt(0) + elementName);
        }

        p.sendMessage(" ");
        p.sendMessage("§8================§e [КОНТРАКТЫ НА ОХОТУ] §8================");
        p.sendMessage("§fВы получили новый контракт: " + selected.getDisplayName());
        if (selected != ContractType.ELEMENTAL) {
            p.sendMessage("§fЗадача: §b" + selected.getDescription());
        }
        p.sendMessage("§fНаграда:");
        p.sendMessage("§a• +" + selected.getRepReward() + " репутации ВК");
        if (selected.getRuneTokens() > 0) p.sendMessage("§6• " + selected.getRuneTokens() + "x Древний Жетон Рун");
        if (selected.getArtifactShards() > 0) p.sendMessage("§d• " + selected.getArtifactShards() + "x Осколок Древнего Артефакта");
        p.sendMessage("§8======================================================");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
    }

    public void handleMobKill(Player p, int rank, boolean isMiniBoss, boolean isSuperBoss) {
        handleMobKill(p, rank, isMiniBoss, isSuperBoss, null, null);
    }

    public void handleMobKill(Player p, int rank, boolean isMiniBoss, boolean isSuperBoss, String element) {
        handleMobKill(p, rank, isMiniBoss, isSuperBoss, element, null);
    }

    public void handleMobKill(Player p, int rank, boolean isMiniBoss, boolean isSuperBoss, String element, org.bukkit.entity.LivingEntity mob) {
        ContractType contract = getActiveContract(p);
        if (contract == null) return;

        boolean qualifies = false;
        if (contract == ContractType.RECRUIT && rank >= 3) qualifies = true;
        else if (contract == ContractType.ELITE && rank >= 6) qualifies = true;
        else if (contract == ContractType.CHAMPION && isMiniBoss) qualifies = true;
        else if (contract == ContractType.LEGENDARY && isSuperBoss) qualifies = true;
        else if (contract == ContractType.ELEMENTAL && element != null) {
            String contractElement = p.getPersistentDataContainer().getOrDefault(contractElementKey, PersistentDataType.STRING, "");
            if (element.equalsIgnoreCase(contractElement)) qualifies = true;
        } else if (contract == ContractType.SLAYER) qualifies = true; // Убийство любого моба засчитывается
        else if (contract == ContractType.ARCHETYPE_SLAYER && mob != null) {
            if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "hardcore_archetype"), PersistentDataType.STRING)) qualifies = true;
        }
        else if (contract == ContractType.BOSS_HUNTER && isMiniBoss) qualifies = true;
        else if (contract == ContractType.STORM_BREAKER && mob != null) {
            if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "from_mob_storm"), PersistentDataType.INTEGER)) qualifies = true;
        } else if (contract == ContractType.VOID_CLEANSER && mob != null) {
            if (mob.getType() == org.bukkit.entity.EntityType.ENDERMAN || mob.getType() == org.bukkit.entity.EntityType.ENDERMITE) qualifies = true;
        } else if (contract == ContractType.SIEGE_DEFENDER && mob != null) {
            if (mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "is_siege_monster"), PersistentDataType.INTEGER)) qualifies = true;
        }

        if (!qualifies) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        int progress = getProgress(p) + 1;
        int required = contract.getRequired();

        if (progress >= required) {
            // Контракт выполнен!
            pdc.remove(contractTypeKey);
            pdc.remove(progressKey);
            pdc.remove(requiredKey);
            pdc.set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis());
            pdc.set(completedKey, PersistentDataType.INTEGER, getCompletedContracts(p) + 1);

            // Выдаем награды
            int vkId = ru.example.vkchat.util.VKChatBridge.getLinkedVkId(p);
            if (vkId != -1) {
                ru.example.vkchat.util.VKChatBridge.addPoints(vkId, contract.getRepReward());
            }

            p.sendMessage(" ");
            p.sendMessage("§8======================================================");
            p.sendMessage("§a🎉 ПОЗДРАВЛЯЕМ! Вы успешно выполнили охотничий контракт!");
            p.sendMessage("§fВыполнено: " + contract.getDisplayName());
            p.sendMessage("§fПолученные награды:");
            p.sendMessage("§a🔺 +" + contract.getRepReward() + " репутации ВК" + (vkId == -1 ? " (Требуется привязка ВК!)" : ""));
            safeGiveItem(p, new ItemStack(org.bukkit.Material.EMERALD, Math.max(1, contract.getRequired() / 3)));
            p.sendMessage("§a♦ Ванильная награда: изумруды x" + Math.max(1, contract.getRequired() / 3));

            if (contract.getRuneTokens() > 0) {
                ItemStack rt = MobListener.getRuneToken();
                rt.setAmount(contract.getRuneTokens());
                safeGiveItem(p, rt);
                p.sendMessage("§6✨ " + contract.getRuneTokens() + "x Древний Жетон Рун");
            }

            if (contract.getArtifactShards() > 0) {
                ItemStack as = MobListener.getArtifactShard();
                as.setAmount(contract.getArtifactShards());
                safeGiveItem(p, as);
                p.sendMessage("§d✨ " + contract.getArtifactShards() + "x Осколок Древнего Артефакта");
            }
            p.sendMessage("§8======================================================");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

        } else {
            // Обновляем прогресс
            pdc.set(progressKey, PersistentDataType.INTEGER, progress);
            p.sendMessage("§a[Контракт] Прогресс: " + progress + "/" + required + " для " + contract.getDisplayName());
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        }
    }

    private void safeGiveItem(Player p, ItemStack item) {
        if (p.getInventory().firstEmpty() == -1) {
            p.getWorld().dropItemNaturally(p.getLocation(), item);
        } else {
            p.getInventory().addItem(item);
        }
    }
}
