package ru.example.vkchatmobs.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.example.vkchatmobs.VKChatMobsPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BossAbilityRegistry — конфиг-управляемый реестр способностей супер-боссов.
 *
 * FIX #1:  Вынесено из MobListener.startBossAbilitiesTask() (~300 строк хардкода).
 * FIX #7:  spawnSuperBoss() использует BossDef вместо хардкода.
 * IMPROVE #1: Способности боссов настраиваются из config.yml.
 * IMPROVE #6: BossDef — data class с HP, уроном, порогами фаз, способностями.
 * IMPROVE #9: Переход фаз использует BossDef вместо if-else.
 */
public class BossAbilityRegistry {

    private final VKChatMobsPlugin plugin;
    private final Map<String, BossDef> bossDefs = new LinkedHashMap<>();
    private final Map<String, Long> lastSpellTime = new ConcurrentHashMap<>();

    // ═══ Data Classes ═══

    public static final class BossDef {
        private final String id;
        private final String displayName;
        private final double baseHp;
        private final double baseDamage;
        private final double phase2Threshold;
        private final List<String> phase2Effects;
        private final List<AbilityDef> abilities;
        private final String auraParticle;
        private final String auraDustColor;
        private final String phase2BroadcastMessage;
        private final String phase2Sound;

        public BossDef(String id, String displayName, double baseHp, double baseDamage,
                       double phase2Threshold, List<String> phase2Effects, List<AbilityDef> abilities,
                       String auraParticle, String auraDustColor,
                       String phase2BroadcastMessage, String phase2Sound) {
            this.id = id;
            this.displayName = displayName;
            this.baseHp = baseHp;
            this.baseDamage = baseDamage;
            this.phase2Threshold = phase2Threshold;
            this.phase2Effects = phase2Effects != null ? phase2Effects : List.of();
            this.abilities = abilities != null ? abilities : List.of();
            this.auraParticle = auraParticle;
            this.auraDustColor = auraDustColor;
            this.phase2BroadcastMessage = phase2BroadcastMessage;
            this.phase2Sound = phase2Sound;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public double getBaseHp() { return baseHp; }
        public double getBaseDamage() { return baseDamage; }
        public double getPhase2Threshold() { return phase2Threshold; }
        public List<String> getPhase2Effects() { return phase2Effects; }
        public List<AbilityDef> getAbilities() { return abilities; }
        public String getAuraParticle() { return auraParticle; }
        public String getAuraDustColor() { return auraDustColor; }
        public String getPhase2BroadcastMessage() { return phase2BroadcastMessage; }
        public String getPhase2Sound() { return phase2Sound; }
    }

    public static final class AbilityDef {
        private final String id;
        private final String broadcastMessage;
        private final int minPhase;
        private final long cooldownMs;
        private final double range;
        private final double damage;
        private final String particleType;
        private final String sound;
        private final List<String> targetEffects;
        private final String specialAction;

        public AbilityDef(String id, String broadcastMessage, int minPhase, long cooldownMs,
                          double range, double damage, String particleType, String sound,
                          List<String> targetEffects, String specialAction) {
            this.id = id;
            this.broadcastMessage = broadcastMessage;
            this.minPhase = minPhase;
            this.cooldownMs = cooldownMs;
            this.range = range;
            this.damage = damage;
            this.particleType = particleType;
            this.sound = sound;
            this.targetEffects = targetEffects;
            this.specialAction = specialAction;
        }

        public String getId() { return id; }
        public String getBroadcastMessage() { return broadcastMessage; }
        public int getMinPhase() { return minPhase; }
        public long getCooldownMs() { return cooldownMs; }
        public double getRange() { return range; }
        public double getDamage() { return damage; }
        public String getParticleType() { return particleType; }
        public String getSound() { return sound; }
        public List<String> getTargetEffects() { return targetEffects; }
        public String getSpecialAction() { return specialAction; }
    }

    public BossAbilityRegistry(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    /**
     * Регистрируем дефолтные определения боссов (обратная совместимость).
     * IMPROVE #1: Конфиг-загрузка будет добавлена позже.
     */
    private void registerDefaults() {
        // Warlord — Древний Воевода
        List<AbilityDef> warlordAbilities = new ArrayList<>();
        warlordAbilities.add(new AbilityDef(
            "spin_attack", "§c[Древний Воевода] РАССЕКАЮЩИЙ УДАР КЛИНКА!",
            2, 8000L, 6.0, 8.0,
            "SWEEP_ATTACK", "ENTITY_PLAYER_ATTACK_SWEEP",
            List.of("KNOCKUP:0.5"), null
        ));
        bossDefs.put("warlord", new BossDef("warlord", "Древний Воевода", 500.0, 15.0,
                0.5, List.of("SPEED:1:12000", "INCREASE_DAMAGE:0:12000"), warlordAbilities,
                "REDSTONE", "RED",
                "§c[Древний Воевода] МОЯ КРОВЬ КИПИТ! ПОЗНАЙТЕ ИСТИННУЮ ЯРОСТЬ КЛИНКА!",
                "ENTITY_ENDER_DRAGON_GROWL"));

        // Storm — Повелитель Бури
        List<AbilityDef> stormAbilities = new ArrayList<>();
        stormAbilities.add(new AbilityDef(
            "tornado", "§b[Повелитель Бури] ПОДЧИНИТЕСЬ СИЛЕ УРАГАНА!",
            1, 12000L, 10.0, 5.0,
            "CLOUD", "ENTITY_ENDER_DRAGON_FLAP",
            List.of("SLOW:80:1", "BLINDNESS:60:0", "PULL:0.45"), "tornado_variant"
        ));
        bossDefs.put("storm", new BossDef("storm", "Повелитель Бури", 600.0, 15.0,
                0.5, List.of(), stormAbilities,
                "SOUL_FIRE_FLAME", null,
                "§b[Повелитель Бури] ГРОЗА ПОГЛОТИТ ВАС! ПРЕКЛОНИТЕ КОЛЕНИ ПЕРЕД СТИХИЕЙ!",
                "ENTITY_LIGHTNING_BOLT_THUNDER"));

        // Alchemist — Проклятый Алхимик
        List<AbilityDef> alchemistAbilities = new ArrayList<>();
        alchemistAbilities.add(new AbilityDef(
            "poison_flask", "§d[Проклятый Алхимик] ПОПРОБУЙТЕ МОЙ НОВЫЙ ЯДОВИТЫЙ РЕАГЕНТ!",
            2, 9000L, 5.0, 0,
            "SPELL_WITCH", "ENTITY_SPLASH_POTION_BREAK",
            List.of("POISON:120:1", "WITHER:80:0"), "self_heal_5pct"
        ));
        bossDefs.put("alchemist", new BossDef("alchemist", "Проклятый Алхимик", 550.0, 15.0,
                0.5, List.of(), alchemistAbilities,
                "SPELL_WITCH", null,
                "§d[Проклятый Алхимик] ХА-ХА-ХА! МОИ СМЕРТЕЛЬНЫЕ РЕАГЕНТЫ ГОТОВЫ К РАСПЫЛЕНИЮ!",
                "ENTITY_WITCH_CELEBRATE"));

        // Void Walker — Странник Бездны
        List<AbilityDef> voidAbilities = new ArrayList<>();
        voidAbilities.add(new AbilityDef(
            "teleport", "§5[Странник Бездны] ПРОСТРАНСТВО СКЛАДЫВАЕТСЯ ВОКРУГ МЕНЯ!",
            2, 6000L, 0, 0,
            "REVERSE_PORTAL", "ENTITY_ENDERMAN_TELEPORT",
            List.of(), "teleport_10_blocks"
        ));
        voidAbilities.add(new AbilityDef(
            "void_explosion", "§5[Странник Бездны] БЕЗДНА ПОГЛОЩАЕТ ВСЁ!",
            2, 10000L, 5.0, 12.0,
            "DRAGON_BREATH", "ENTITY_ENDER_DRAGON_GROWL",
            List.of("WITHER:100:1", "BLINDNESS:60:0"), null
        ));
        voidAbilities.add(new AbilityDef(
            "spawn_endermen", "§5[Странник Бездны] МОИ СЛУГИ ПРИБЫВАЮТ ИЗ БЕЗДНЫ!",
            2, 15000L, 0, 0,
            "REVERSE_PORTAL", "ENTITY_ENDERMAN_SCREAM",
            List.of(), "spawn_4_endermen"
        ));
        bossDefs.put("void_walker", new BossDef("void_walker", "Странник Бездны", 700.0, 15.0,
                0.4, List.of("SPEED:1:12000", "INCREASE_DAMAGE:1:12000"), voidAbilities,
                "REVERSE_PORTAL", "80,0,120",
                "§5[Странник Бездны] БЕЗДНА ОТКРЫВАЕТСЯ! ПРОСТРАНСТВО РАЗРЫВАЕТСЯ!",
                "ENTITY_ENDER_DRAGON_GROWL"));

        plugin.getLogger().info("[BossAbility] Загружено " + bossDefs.size() + " определений супер-боссов");
    }

    /**
     * Попробовать загрузить определения боссов из конфига.
     * IMPROVE #1: Конфиг-управляемые боссы.
     */
    public void loadFromConfig() {
        if (!plugin.getConfig().isConfigurationSection("bosses")) return;

        bossDefs.clear();
        for (String bossId : plugin.getConfig().getConfigurationSection("bosses").getKeys(false)) {
            String basePath = "bosses." + bossId;
            String displayName = plugin.getConfig().getString(basePath + ".display-name", bossId);
            double hp = plugin.getConfig().getDouble(basePath + ".hp", 500.0);
            double damage = plugin.getConfig().getDouble(basePath + ".damage", 15.0);
            double threshold = plugin.getConfig().getDouble(basePath + ".phase2-threshold", 0.5);
            List<String> phase2Effects = plugin.getConfig().getStringList(basePath + ".phase2-effects");
            String auraParticle = plugin.getConfig().getString(basePath + ".aura-particle", "");
            String auraDustColor = plugin.getConfig().getString(basePath + ".aura-dust-color", "");
            String phase2Msg = plugin.getConfig().getString(basePath + ".phase2-message", "");
            String phase2Sound = plugin.getConfig().getString(basePath + ".phase2-sound", "");

            List<AbilityDef> abilities = new ArrayList<>();
            if (plugin.getConfig().isConfigurationSection(basePath + ".abilities")) {
                for (String abilityId : plugin.getConfig().getConfigurationSection(basePath + ".abilities").getKeys(false)) {
                    String abPath = basePath + ".abilities." + abilityId;
                    abilities.add(new AbilityDef(
                        abilityId,
                        plugin.getConfig().getString(abPath + ".message", ""),
                        plugin.getConfig().getInt(abPath + ".min-phase", 1),
                        plugin.getConfig().getLong(abPath + ".cooldown-ms", 8000L),
                        plugin.getConfig().getDouble(abPath + ".range", 5.0),
                        plugin.getConfig().getDouble(abPath + ".damage", 0),
                        plugin.getConfig().getString(abPath + ".particle", ""),
                        plugin.getConfig().getString(abPath + ".sound", ""),
                        plugin.getConfig().getStringList(abPath + ".target-effects"),
                        plugin.getConfig().getString(abPath + ".special-action", "")
                    ));
                }
            }

            bossDefs.put(bossId, new BossDef(bossId, displayName, hp, damage,
                    threshold, phase2Effects, abilities, auraParticle, auraDustColor,
                    phase2Msg, phase2Sound));
        }
        plugin.getLogger().info("[BossAbility] Загружено " + bossDefs.size() + " боссов из конфига");
    }

    // ═══ API ═══

    public BossDef getBossDef(String bossType) {
        return bossDefs.get(bossType);
    }

    public Collection<BossDef> getAllBossDefs() {
        return Collections.unmodifiableCollection(bossDefs.values());
    }

    public BossDef getRandomBossDef() {
        List<BossDef> list = new ArrayList<>(bossDefs.values());
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    // ═══ Ауры ═══

    /**
     * Спавнит ауру-частицы вокруг супер-босса.
     * Заменяет хардкод if-else из MobListener.startBossAbilitiesTask().
     */
    public void spawnAuraParticles(LivingEntity mob, String bossType) {
        BossDef def = bossDefs.get(bossType);
        if (def == null) return;

        String auraType = def.getAuraParticle();
        if (auraType == null || auraType.isEmpty()) return;

        try {
            Particle particle = Particle.valueOf(auraType);
            if (particle == Particle.REDSTONE) {
                String dustColor = def.getAuraDustColor();
                org.bukkit.Color color = parseColor(dustColor);
                mob.getWorld().spawnParticle(particle, mob.getLocation().add(0, 0.2, 0), 8, 0.5, 0.2, 0.5, 0.01,
                        new Particle.DustOptions(color, 1.5f));
            } else {
                mob.getWorld().spawnParticle(particle, mob.getLocation().add(0, 0.3, 0), 8, 0.5, 0.3, 0.5, 0.02);
            }
        } catch (Exception ignored) {}
    }

    private org.bukkit.Color parseColor(String rgb) {
        if (rgb == null || rgb.isEmpty()) return org.bukkit.Color.RED;
        try {
            String[] parts = rgb.split(",");
            return org.bukkit.Color.fromRGB(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (Exception e) { return org.bukkit.Color.RED; }
    }

    // ═══ Способности ═══

    /**
     * Выполнить способность босса.
     */
    public void executeAbility(LivingEntity mob, BossDef bossDef, AbilityDef ability, List<Player> nearbyPlayers) {
        long now = System.currentTimeMillis();
        String cdKey = mob.getUniqueId().toString() + "_" + ability.getId();

        Long lastUsed = lastSpellTime.get(cdKey);
        long cd = ability.getCooldownMs();
        // Для шторма кулдаун сокращается во 2 фазе
        if (bossDef.getId().equals("storm") && "tornado".equals(ability.getId())) {
            int phase = mob.getPersistentDataContainer().getOrDefault(
                    new NamespacedKey(plugin, "boss_phase"), PersistentDataType.INTEGER, 1);
            if (phase == 2) cd = 6000L;
        }
        if (lastUsed != null && now - lastUsed < cd) return;
        lastSpellTime.put(cdKey, now);

        // Броадкаст
        if (ability.getBroadcastMessage() != null && !ability.getBroadcastMessage().isEmpty()) {
            Bukkit.broadcastMessage(ability.getBroadcastMessage());
        }

        // Звук
        try {
            Sound sound = Sound.valueOf(ability.getSound());
            mob.getWorld().playSound(mob.getLocation(), sound, 1.5f, 0.5f);
        } catch (Exception ignored) {}

        // Частицы
        spawnAbilityParticles(mob, ability);

        // Применяем к ближайшим игрокам
        for (Player p : nearbyPlayers) {
            if (ability.getRange() > 0 && p.getLocation().distance(mob.getLocation()) <= ability.getRange()) {
                applyTargetEffects(p, ability);
                if (ability.getDamage() > 0) {
                    p.damage(ability.getDamage(), mob);
                }
            }
        }

        // Специальные действия
        applySpecialAction(mob, ability, nearbyPlayers);
    }

    private void spawnAbilityParticles(LivingEntity mob, AbilityDef ability) {
        String particleType = ability.getParticleType();
        try {
            Particle particle = Particle.valueOf(particleType);
            switch (particleType) {
                case "SWEEP_ATTACK":
                    mob.getWorld().spawnParticle(particle, mob.getLocation().add(0, 1, 0), 10, 2.0, 0.5, 2.0, 0.1);
                    break;
                case "CLOUD":
                    for (int h = 0; h < 6; h++) {
                        mob.getWorld().spawnParticle(particle, mob.getLocation().add(0, h, 0), 20, 1.0, 0.2, 1.0, 0.1);
                    }
                    break;
                case "REVERSE_PORTAL":
                    mob.getWorld().spawnParticle(particle, mob.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                    break;
                case "DRAGON_BREATH":
                    mob.getWorld().spawnParticle(particle, mob.getLocation(), 100, 5.0, 1.0, 5.0, 0.05);
                    break;
                case "SPELL_WITCH":
                    mob.getWorld().spawnParticle(particle, mob.getLocation().add(0, 0.5, 0), 6, 0.5, 0.5, 0.5, 0.02);
                    break;
                default:
                    mob.getWorld().spawnParticle(particle, mob.getLocation().add(0, 1, 0), 30, 1.0, 1.0, 1.0, 0.1);
                    break;
            }
        } catch (Exception ignored) {}
    }

    private void applyTargetEffects(Player p, AbilityDef ability) {
        if (ability.getTargetEffects() == null) return;
        for (String effectStr : ability.getTargetEffects()) {
            applyEffectString(p, effectStr);
        }
    }

    private void applyEffectString(Player p, String effectStr) {
        try {
            String[] parts = effectStr.split(":");
            String type = parts[0];

            switch (type) {
                case "KNOCKUP":
                    double height = parts.length > 1 ? Double.parseDouble(parts[1]) : 0.5;
                    p.setVelocity(new Vector(0, height, 0));
                    break;
                case "PULL":
                    // Обрабатывается в specialAction
                    break;
                default:
                    PotionEffectType pet = PotionEffectType.getByName(type);
                    if (pet != null) {
                        int duration = parts.length > 1 ? Integer.parseInt(parts[1]) : 60;
                        int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                        p.addPotionEffect(new PotionEffect(pet, duration, amplifier));
                    }
                    break;
            }
        } catch (Exception ignored) {}
    }

    private void applySpecialAction(LivingEntity mob, AbilityDef ability, List<Player> nearbyPlayers) {
        String action = ability.getSpecialAction();
        if (action == null) return;

        switch (action) {
            case "tornado_variant":
                for (Player p : nearbyPlayers) {
                    if (p.getLocation().distance(mob.getLocation()) <= 10.0) {
                        Vector dir = mob.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.45);
                        p.setVelocity(dir);
                    }
                }
                break;
            case "self_heal_5pct":
                AttributeInstance hpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hpAttr != null) {
                    double maxHp = hpAttr.getValue();
                    double heal = maxHp * 0.05;
                    mob.setHealth(Math.min(maxHp, mob.getHealth() + heal));
                    mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.2f, 1.0f);
                    mob.getWorld().spawnParticle(Particle.SPELL_INSTANT, mob.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.05);
                }
                break;
            case "teleport_10_blocks":
                double tx = mob.getLocation().getX() + (ThreadLocalRandom.current().nextDouble() * 20 - 10);
                double tz = mob.getLocation().getZ() + (ThreadLocalRandom.current().nextDouble() * 20 - 10);
                double ty = mob.getWorld().getHighestBlockYAt((int) tx, (int) tz) + 1;
                mob.teleport(new Location(mob.getWorld(), tx, ty, tz));
                mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mob.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
                break;
            case "spawn_4_endermen":
                for (int i = 0; i < 4; i++) {
                    org.bukkit.entity.Entity enderman = mob.getWorld().spawnEntity(
                            mob.getLocation().add(ThreadLocalRandom.current().nextDouble() * 4 - 2, 0,
                                    ThreadLocalRandom.current().nextDouble() * 4 - 2),
                            org.bukkit.entity.EntityType.ENDERMAN);
                    if (enderman instanceof LivingEntity) {
                        LivingEntity em = (LivingEntity) enderman;
                        em.setCustomName("§5Слуга Бездны");
                        em.setCustomNameVisible(false);
                        AttributeInstance emHp = em.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (emHp != null) {
                            emHp.setBaseValue(20.0);
                            em.setHealth(20.0);
                        }
                    }
                }
                mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mob.getLocation(), 80, 2.0, 1.0, 2.0, 0.1);
                break;
        }
    }

    // ═══ Фазовый переход ═══

    /**
     * Обработать переход босса во 2 фазу.
     * IMPROVE #9: Использует BossDef вместо хардкода.
     */
    public void handlePhaseTransition(LivingEntity mob, BossDef bossDef, int newPhase) {
        if (newPhase == 2) {
            // Зельные эффекты при фазе 2
            for (String effectStr : bossDef.getPhase2Effects()) {
                try {
                    String[] parts = effectStr.split(":");
                    PotionEffectType pet = PotionEffectType.getByName(parts[0]);
                    if (pet != null) {
                        int duration = parts.length > 1 ? Integer.parseInt(parts[1]) : 12000;
                        int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                        mob.addPotionEffect(new PotionEffect(pet, duration, amplifier));
                    }
                } catch (Exception ignored) {}
            }

            // Броадкаст фазового сообщения из BossDef
            if (bossDef.getPhase2BroadcastMessage() != null && !bossDef.getPhase2BroadcastMessage().isEmpty()) {
                Bukkit.broadcastMessage(bossDef.getPhase2BroadcastMessage());
            }

            // Звук
            if (bossDef.getPhase2Sound() != null && !bossDef.getPhase2Sound().isEmpty()) {
                try {
                    mob.getWorld().playSound(mob.getLocation(), Sound.valueOf(bossDef.getPhase2Sound()), 2.0f, 0.6f);
                } catch (Exception ignored) {}
            }

            // Фазовые частицы (boss-specific)
            spawnPhase2Particles(mob, bossDef);

            // Спецдействия при фазовом переходе
            if (bossDef.getId().equals("storm")) {
                mob.getWorld().strikeLightningEffect(mob.getLocation());
            }
            if (bossDef.getId().equals("void_walker")) {
                // Призыв эндерменов при входе во 2 фазу
                for (int i = 0; i < 4; i++) {
                    org.bukkit.entity.Entity enderman = mob.getWorld().spawnEntity(
                            mob.getLocation().add(ThreadLocalRandom.current().nextDouble() * 4 - 2, 0,
                                    ThreadLocalRandom.current().nextDouble() * 4 - 2),
                            org.bukkit.entity.EntityType.ENDERMAN);
                    if (enderman instanceof LivingEntity) {
                        LivingEntity em = (LivingEntity) enderman;
                        em.setCustomName("§5Слуга Бездны");
                        em.setCustomNameVisible(false);
                    }
                }
            }
        }
    }

    /**
     * Спавнит частицы фазового перехода.
     */
    private void spawnPhase2Particles(LivingEntity mob, BossDef bossDef) {
        String aura = bossDef.getAuraParticle();
        if (aura == null || aura.isEmpty()) return;

        try {
            Particle particle = Particle.valueOf(aura);
            switch (bossDef.getId()) {
                case "warlord":
                    mob.getWorld().spawnParticle(Particle.REDSTONE, mob.getLocation(), 100, 1.0, 1.0, 1.0, 0.1,
                            new Particle.DustOptions(Color.RED, 2.0f));
                    break;
                case "storm":
                    mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                    mob.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                    break;
                case "alchemist":
                    mob.getWorld().spawnParticle(Particle.SPELL_WITCH, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                    break;
                case "void_walker":
                    mob.getWorld().spawnParticle(Particle.REVERSE_PORTAL, mob.getLocation(), 150, 2.0, 2.0, 2.0, 0.1);
                    mob.getWorld().spawnParticle(Particle.DRAGON_BREATH, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.05);
                    break;
                default:
                    mob.getWorld().spawnParticle(particle, mob.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
                    break;
            }
        } catch (Exception ignored) {}
    }

    // ═══ Утилиты ═══

    public void setSpellTime(String key, long time) {
        lastSpellTime.put(key, time);
    }

    public long getSpellTime(String key) {
        return lastSpellTime.getOrDefault(key, 0L);
    }

    public void cleanup(long now) {
        lastSpellTime.entrySet().removeIf(e -> now - e.getValue() > 600000);
    }
}
