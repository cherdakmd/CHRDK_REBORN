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

import java.util.Random;

public class ContractManager {
    private final VKChatMobsPlugin plugin;
    private final Random random = new Random();

    private final NamespacedKey contractTypeKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey requiredKey;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey completedKey;

    public ContractManager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.contractTypeKey = new NamespacedKey(plugin, "mobs_contract_type");
        this.progressKey = new NamespacedKey(plugin, "mobs_contract_progress");
        this.requiredKey = new NamespacedKey(plugin, "mobs_contract_required");
        this.cooldownKey = new NamespacedKey(plugin, "mobs_contract_last_reset");
        this.completedKey = new NamespacedKey(plugin, "mobs_contracts_completed");
    }

    public enum ContractType {
        RECRUIT("recruit", "§aОхотник на Новобранцев", "Убить 15 мобов Ранга 3+", 15, 150, 1, 0),
        ELITE("elite", "§dИстребитель Элиты", "Убить 10 мобов Ранга 6+", 10, 250, 2, 0),
        CHAMPION("champion", "§6Гроза Чемпионов", "Убить 3 Мини-Bossов", 3, 350, 1, 1),
        LEGENDARY("legendary", "§c☠ Легендарный Завоеватель ☠", "Убить 1 Мирового Супер-Босса", 1, 500, 3, 2);

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
        long nextAvailable = lastReset + 86400000L; // 24 часа
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
        try {
            org.bukkit.plugin.Plugin jobs = Bukkit.getPluginManager().getPlugin("VKChatJobs");
            if (jobs != null && jobs.isEnabled()) {
                Object dm = jobs.getClass().getMethod("getJobsDataManager").invoke(jobs);
                return (int) dm.getClass().getMethod("getLevel", java.util.UUID.class, String.class).invoke(dm, p.getUniqueId(), "hunter");
            }
        } catch (Throwable ignored) {}
        return 1;
    }

    public void generateContract(Player p) {
        ContractType[] types = ContractType.values();
        int completed = getCompletedContracts(p);
        int hunter = getHunterJobLevel(p);
        int maxIndex = 0;
        if (completed >= 3 || hunter >= 10) maxIndex = 1;
        if (completed >= 10 || hunter >= 25) maxIndex = 2;
        if (completed >= 25 || hunter >= 40) maxIndex = 3;
        ContractType selected = types[random.nextInt(Math.min(types.length, maxIndex + 1))];

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(contractTypeKey, PersistentDataType.STRING, selected.getId());
        pdc.set(progressKey, PersistentDataType.INTEGER, 0);
        pdc.set(requiredKey, PersistentDataType.INTEGER, selected.getRequired());

        p.sendMessage(" ");
        p.sendMessage("§8================§e [КОНТРАКТЫ НА ОХОТУ] §8================");
        p.sendMessage("§fВы получили новый контракт: " + selected.getDisplayName());
        p.sendMessage("§fЗадача: §b" + selected.getDescription());
        p.sendMessage("§fНаграда:");
        p.sendMessage("§a• +" + selected.getRepReward() + " репутации ВК");
        if (selected.getRuneTokens() > 0) p.sendMessage("§6• " + selected.getRuneTokens() + "x Древний Жетон Рун");
        if (selected.getArtifactShards() > 0) p.sendMessage("§d• " + selected.getArtifactShards() + "x Осколок Древнего Артефакта");
        p.sendMessage("§8======================================================");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
    }

    public void handleMobKill(Player p, int rank, boolean isMiniBoss, boolean isSuperBoss) {
        ContractType contract = getActiveContract(p);
        if (contract == null) return;

        boolean qualifies = false;
        if (contract == ContractType.RECRUIT && rank >= 3) qualifies = true;
        else if (contract == ContractType.ELITE && rank >= 6) qualifies = true;
        else if (contract == ContractType.CHAMPION && isMiniBoss) qualifies = true;
        else if (contract == ContractType.LEGENDARY && isSuperBoss) qualifies = true;

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
            int vkId = VKChatPlugin.getInstance().getAuthManager().getLinkedVkId(p);
            if (vkId != -1) {
                VKChatPlugin.getInstance().getReputationManager().addPoints(vkId, contract.getRepReward());
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
