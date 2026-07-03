package ru.example.vkchatmobs.siege;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ru.example.vkchatmobs.VKChatMobsPlugin;
import ru.example.vkchatmobs.listeners.MobListener;
import ru.example.vkchatnations.VKChatNationsPlugin;
import ru.example.vkchatnations.data.ChunkClaim;
import ru.example.vkchatnations.data.NationManager;
import ru.example.vkchat.VKChatPlugin;
import ru.example.vkchatmobs.util.VKChatBridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SiegeManager {
    private final VKChatMobsPlugin plugin;

    private final NamespacedKey isSiegeMonsterKey;
    private final NamespacedKey siegeKey;

    private final Map<String, ActiveSiege> activeSieges = new ConcurrentHashMap<>();

    public SiegeManager(VKChatMobsPlugin plugin) {
        this.plugin = plugin;
        this.isSiegeMonsterKey = new NamespacedKey(plugin, "is_siege_monster");
        this.siegeKey = new NamespacedKey(plugin, "siege_key");

        startSiegeTask();
    }

    public static class ActiveSiege {
        public final String claimKey;
        public final ChunkClaim claim;
        public final Location blockLoc;
        public final List<UUID> aliveMonsters = new ArrayList<>();
        public int currentWave = 1;
        public int totalWaves = 3;
        public int state = 0; // 0 = spawning wave, 1 = fighting wave, 2 = wave transition, 3 = finished
        public long lastActionTime;
        public long lastBlockDamageTime;

        public ActiveSiege(String claimKey, ChunkClaim claim, Location blockLoc) {
            this.claimKey = claimKey;
            this.claim = claim;
            this.blockLoc = blockLoc;
            this.lastActionTime = System.currentTimeMillis();
            this.lastBlockDamageTime = System.currentTimeMillis();
        }
    }

    private void startSiegeTask() {
        // Проверка каждую минуту (1200 тиков)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Проверяем активность Кровавой Луны
                boolean bloodMoonActive = false;
                try {
                    if (VKChatPlugin.getInstance() != null && 
                        VKChatPlugin.getInstance().getBloodMoonManager() != null) {
                        bloodMoonActive = VKChatPlugin.getInstance().getBloodMoonManager().isActive();
                    }
                } catch (Throwable ignored) {}

                if (!bloodMoonActive) {
                    // Если Кровавая Луна закончилась, отменяем все активные осады
                    if (!activeSieges.isEmpty()) {
                        for (String key : new ArrayList<>(activeSieges.keySet())) {
                            stopSiege(key, false, "Кровавая Луна зашла, осада снята!");
                        }
                    }
                    return;
                }

                // Каждую минуту есть 25% шанс запустить осаду у случайного онлайн игрока с приватом
                if (ThreadLocalRandom.current().nextInt(100) < 25) {
                    triggerRandomSiege();
                }

                // Обработка активных осад (AI монстров и нанесение урона блокам)
                tickSieges();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Раз в минуту

        // Быстрый обработчик физического поведения монстров (каждые 3 секунды)
        new BukkitRunnable() {
            @Override
            public void run() {
                tickSiegeMonsters();
            }
        }.runTaskTimer(plugin, 60L, 60L);
    }

    private void triggerRandomSiege() {
        VKChatNationsPlugin nationsPlugin = (VKChatNationsPlugin) Bukkit.getPluginManager().getPlugin("VKChatNations");
        if (nationsPlugin == null || !nationsPlugin.isEnabled()) return;

        NationManager nm = nationsPlugin.getNationManager();
        if (nm == null) return;

        List<Map.Entry<String, ChunkClaim>> eligibleClaims = new ArrayList<>();
        for (Map.Entry<String, ChunkClaim> entry : nm.getNationClaims().entrySet()) {
            ChunkClaim claim = entry.getValue();
            if (activeSieges.containsKey(entry.getKey())) continue;

            // Проверяем, онлайн ли владелец привата
            Player owner = Bukkit.getPlayer(claim.getOwner());
            if (owner != null && owner.isOnline() && owner.getWorld().getName().equals(claim.getWorldName())) {
                eligibleClaims.add(entry);
            }
        }

        if (eligibleClaims.isEmpty()) return;

        // Выбираем случайный приват
        Map.Entry<String, ChunkClaim> chosen = eligibleClaims.get(ThreadLocalRandom.current().nextInt(eligibleClaims.size()));
        String key = chosen.getKey();
        ChunkClaim claim = chosen.getValue();

        World world = Bukkit.getWorld(claim.getWorldName());
        if (world == null) return;

        Location blockLoc = new Location(world, claim.getX(), claim.getY(), claim.getZ());
        ActiveSiege siege = new ActiveSiege(key, claim, blockLoc);
        activeSieges.put(key, siege);

        // Уведомление
        String alert = "§c☠️ [ОСАДА КРОВЯНОЙ ЛУНЫ] §eОрда монстров обнаружила ваш блок привата на координатах §bX: " + claim.getX() + " Y: " + claim.getY() + " Z: " + claim.getZ() + "§e! Они наступают! Спешите на защиту!";
        nm.broadcastToNationWithPrefix(claim.getNation(), alert);

        Player owner = Bukkit.getPlayer(claim.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.playSound(owner.getLocation(), Sound.EVENT_RAID_HORN, 2.0f, 1.0f);
        }

        // Запуск первой волны
        spawnWave(siege);
    }

    private void spawnWave(ActiveSiege siege) {
        siege.state = 0; // Spawning
        int baseCount = plugin.getConfig().getInt("siege.wave-base-count", 4);
        int countPerWave = plugin.getConfig().getInt("siege.wave-per-wave", 3);
        int count = baseCount + (siege.currentWave * countPerWave);
        World world = siege.blockLoc.getWorld();
        if (world == null) return;

        String nation = siege.claim.getNation();
        VKChatNationsPlugin nationsPlugin = (VKChatNationsPlugin) Bukkit.getPluginManager().getPlugin("VKChatNations");

        if (nationsPlugin != null) {
            nationsPlugin.getNationManager().broadcastToNationWithPrefix(nation, "§c☠ [ОСАДА] Надвигается Волна " + siege.currentWave + "/" + siege.totalWaves + "! Будьте наготове!");
        }

        // Спавним монстров вокруг блока привата в радиусе 12-16 блоков
        EntityType[] types = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER};

        for (int i = 0; i < count; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double r = 12 + ThreadLocalRandom.current().nextDouble() * 4;
            double dx = Math.cos(angle) * r;
            double dz = Math.sin(angle) * r;

            Location spawnLoc = siege.blockLoc.clone().add(dx, 0, dz);
            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

            EntityType t = types[ThreadLocalRandom.current().nextInt(types.length)];
            
            // Если 3 волна, последний монстр может быть Мини-Боссом
            boolean isWaveBoss = (siege.currentWave == 3 && i == count - 1);

            Entity entity = world.spawnEntity(spawnLoc, t);
            if (entity instanceof LivingEntity) {
                LivingEntity mob = (LivingEntity) entity;
                
                // Настраиваем как осадного монстра
                mob.getPersistentDataContainer().set(isSiegeMonsterKey, PersistentDataType.INTEGER, 1);
                mob.getPersistentDataContainer().set(siegeKey, PersistentDataType.STRING, siege.claimKey);
                
                // Ранг 8 для осадных монстров
                mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_rank"), PersistentDataType.INTEGER, 8);
                mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "difficulty_multiplier"), PersistentDataType.DOUBLE, 2.5);

                mob.setGlowing(true);

                if (isWaveBoss) {
                    mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "is_mini_boss"), PersistentDataType.INTEGER, 1);
                    mob.setCustomName("§c☠ Осадный Военачальник ☠");
                    mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "mob_rank"), PersistentDataType.INTEGER, 10);
                    mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "difficulty_multiplier"), PersistentDataType.DOUBLE, 4.0);
                } else {
                    mob.setCustomName("§c☠ Осадный Разрушитель ☠");
                }
                mob.setCustomNameVisible(true);

                // Даем силу и здоровье
                org.bukkit.attribute.AttributeInstance hp = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) {
                    double mult = isWaveBoss ? 4.0 : 2.5;
                    hp.setBaseValue(hp.getBaseValue() * mult);
                    mob.setHealth(hp.getBaseValue());
                    mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "mobs_scaled"), PersistentDataType.INTEGER, 1);
                }

                siege.aliveMonsters.add(mob.getUniqueId());

                // Красивые частицы при спавне
                world.spawnParticle(org.bukkit.Particle.FLAME, spawnLoc, 20, 0.5, 1.0, 0.5, 0.1);
                world.playSound(spawnLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.5f);
            }
        }

        siege.state = 1; // Fighting
        siege.lastActionTime = System.currentTimeMillis();
    }

    private void tickSieges() {
        for (Map.Entry<String, ActiveSiege> entry : activeSieges.entrySet()) {
            ActiveSiege siege = entry.getValue();
            if (siege.state == 3) continue;

            // Проверяем, существует ли еще блок привата физически
            World world = siege.blockLoc.getWorld();
            if (world == null) continue;

            Block block = world.getBlockAt(siege.blockLoc);
            if (block.getType() == Material.AIR) {
                stopSiege(entry.getKey(), false, "Блок привата разрушен физически или удален!");
                continue;
            }

            // Очищаем мертвых монстров из списка
            siege.aliveMonsters.removeIf(uuid -> {
                Entity e = Bukkit.getEntity(uuid);
                return e == null || e.isDead() || !e.isValid();
            });

            // Если все монстры убиты
            if (siege.aliveMonsters.isEmpty() && siege.state == 1) {
                if (siege.currentWave < siege.totalWaves) {
                    siege.currentWave++;
                    siege.state = 2; // Transition
                    
                    String nation = siege.claim.getNation();
                    VKChatNationsPlugin nationsPlugin = (VKChatNationsPlugin) Bukkit.getPluginManager().getPlugin("VKChatNations");
                    if (nationsPlugin != null) {
                        nationsPlugin.getNationManager().broadcastToNationWithPrefix(nation, "§a✓ Волна успешно отбита! Подготовка к Волне " + siege.currentWave + "...");
                    }

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (activeSieges.containsKey(siege.claimKey)) {
                            spawnWave(siege);
                        }
                    }, 200L); // 10 секунд передышки
                } else {
                    // Все волны побеждены! Победа!
                    stopSiege(siege.claimKey, true, "Победа!");
                }
            }
        }
    }

    private void tickSiegeMonsters() {
        for (ActiveSiege siege : activeSieges.values()) {
            if (siege.state != 1) continue;

            World world = siege.blockLoc.getWorld();
            if (world == null) continue;

            long now = System.currentTimeMillis();
            boolean damageApplied = false;

            for (UUID uuid : siege.aliveMonsters) {
                Entity e = Bukkit.getEntity(uuid);
                if (e instanceof Monster && !e.isDead() && e.isValid()) {
                    Monster m = (Monster) e;

                    // Направляем монстра к блоку привата
                    m.setTarget(null);
                    Location blockL = siege.blockLoc;
                    double dist = m.getLocation().distance(blockL);

                    if (dist > 3.0) {
                        // Двигаем монстра в сторону блока привата с помощью вектора скорости
                        Vector direction = blockL.toVector().subtract(m.getLocation().toVector()).normalize().multiply(0.2);
                        m.setVelocity(direction);
                    } else {
                        // Монстр у самого блока привата — атакует его!
                        if (now - siege.lastBlockDamageTime >= plugin.getConfig().getInt("siege.damage-interval-ms", 10000)) {
                            damageApplied = true;
                        }
                    }

                    // Красивый след частиц за осадным монстром
                    world.spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, m.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.05);
                }
            }

            if (damageApplied) {
                siege.lastBlockDamageTime = now;
                ChunkClaim claim = siege.claim;
                claim.setDurability(Math.max(0, claim.getDurability() - plugin.getConfig().getInt("siege.damage-per-tick", 8)));

                // Звук и частицы поломки блока
                world.playSound(siege.blockLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
                world.spawnParticle(org.bukkit.Particle.BLOCK_CRACK, siege.blockLoc.clone().add(0.5, 0.5, 0.5), 40, 0.3, 0.3, 0.3, Material.OBSIDIAN.createBlockData());

                VKChatNationsPlugin nationsPlugin = (VKChatNationsPlugin) Bukkit.getPluginManager().getPlugin("VKChatNations");
                if (nationsPlugin != null) {
                    nationsPlugin.getNationManager().broadcastToNationWithPrefix(claim.getNation(), 
                        "§c⚠️ [ОСАДА] Монстры прорвались к блоку привата! Прочность снижена! Текущая прочность: §e" + claim.getDurability() + "%"
                    );
                }

                if (claim.getDurability() <= 0) {
                    // Разрушаем приват
                    stopSiege(siege.claimKey, false, "Блок привата был уничтожен ордой монстров Кровавой Луны!");
                    
                    // Физическое удаление блока
                    Block b = world.getBlockAt(siege.blockLoc);
                    b.setType(Material.AIR);

                    // Удаление из данных
                    if (nationsPlugin != null) {
                        nationsPlugin.getNationManager().getNationClaims().remove(siege.claimKey);
                        nationsPlugin.getNationManager().saveAll();
                        nationsPlugin.getNationManager().broadcastToNationWithPrefix(claim.getNation(), 
                            "§c☠☠ Блок привата на координатах X:" + claim.getX() + " Z:" + claim.getZ() + " был полностью разрушен монстрами!"
                        );
                    }
                }
            }
        }
    }

    public void handleSiegeMonsterKill(Entity mob, Player killer) {
        if (!mob.getPersistentDataContainer().has(isSiegeMonsterKey, PersistentDataType.INTEGER)) return;
        String sKey = mob.getPersistentDataContainer().get(siegeKey, PersistentDataType.STRING);
        if (sKey == null) return;

        ActiveSiege siege = activeSieges.get(sKey);
        if (siege != null) {
            siege.aliveMonsters.remove(mob.getUniqueId());

            mob.getWorld().spawnParticle(org.bukkit.Particle.SOUL, mob.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_VEX_DEATH, 0.8f, 0.5f);

            if (killer != null) {
                killer.sendMessage("§a☠ Вы повергли Осадного Разрушителя! §7[Осталось: " + siege.aliveMonsters.size() + "]");
                try {
                    int vkId = VKChatBridge.getLinkedVkId(killer);
                    if (vkId != -1) VKChatBridge.addPoints(vkId, 12);
                    killer.sendMessage("§a🔺 +12 репутации ВК за убийство осадного монстра!");
                } catch (Throwable ignored) {}
            }
        }
    }

    public void stopSiege(String key, boolean success, String reason) {
        ActiveSiege siege = activeSieges.remove(key);
        if (siege == null) return;

        // Чистим монстров
        for (UUID uuid : siege.aliveMonsters) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }

        String nation = siege.claim.getNation();
        VKChatNationsPlugin nationsPlugin = (VKChatNationsPlugin) Bukkit.getPluginManager().getPlugin("VKChatNations");

        if (success) {
            // Раздаем награды игрокам в радиусе 30 блоков от привата
            List<Player> rewarded = new ArrayList<>();
            for (Player p : siege.blockLoc.getWorld().getPlayers()) {
                if (p.getLocation().distance(siege.blockLoc) <= 30.0) {
                    rewarded.add(p);
                }
            }

            if (nationsPlugin != null) {
                // +1000 репутации в казну нации
                nationsPlugin.getNationManager().depositReputation(nation, 1000);
                nationsPlugin.getNationManager().broadcastToNationWithPrefix(nation, 
                    "§a🎉🎉 ГЕРОИ! Осада Кровавой Луны успешно отбита! Казна Нации пополнена на §e+1000 реп. ВК§a!"
                );
            }

            for (Player p : rewarded) {
                p.sendMessage(" ");
                p.sendMessage("§8======================================================");
                p.sendMessage("§a🎉 [ГЕРОЙ ОСАДЫ] Вы выстояли против натиска Кровавой Луны!");
                p.sendMessage("§fВы удержали блок привата на X: " + siege.claim.getX() + " Z: " + siege.claim.getZ());
                p.sendMessage("§fВаши личные награды:");
                
                int vkId = VKChatBridge.getLinkedVkId(p);
                if (vkId != -1) {
                    VKChatBridge.addPoints(vkId, 500);
                    p.sendMessage("§a🔺 +500 Репутации ВК");
                } else {
                    p.sendMessage("§c🔺 +500 Репутации утеряно (аккаунт ВК не привязан!)");
                }

                ItemStack rt = MobListener.getRuneToken();
                rt.setAmount(2);
                if (p.getInventory().firstEmpty() == -1) {
                    p.getWorld().dropItemNaturally(p.getLocation(), rt);
                } else {
                    p.getInventory().addItem(rt);
                }
                p.sendMessage("§6✨ 2x Древний Жетон Рун");

                ItemStack as = MobListener.getArtifactShard();
                if (p.getInventory().firstEmpty() == -1) {
                    p.getWorld().dropItemNaturally(p.getLocation(), as);
                } else {
                    p.getInventory().addItem(as);
                }
                p.sendMessage("§d✨ 1x Осколок Древнего Артефакта");
                p.sendMessage("§8======================================================");
                
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.8f);
            }
        } else {
            if (nationsPlugin != null) {
                nationsPlugin.getNationManager().broadcastToNationWithPrefix(nation, "§c☠ Осада завершена неудачей. Причина: " + reason);
            }
        }
    }
}
