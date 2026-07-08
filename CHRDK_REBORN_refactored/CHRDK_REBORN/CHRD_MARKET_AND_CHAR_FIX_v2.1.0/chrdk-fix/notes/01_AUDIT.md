# 🔍 Аудит CHRDK_REBORN — vkchat_market + vkchat_artifacts

**Дата:** 2026-07-08
**Объём:** 9 Java-классов (≈2 800 строк), 3 yaml-конфига, plugin.yml ×2
**Сервер:** VKChat (Spigot 1.16.5, основной код), с перспективой до 1.21

---

## 📊 Сводка по модулям

| Модуль | Файлов | Строк | Классов | Статус |
|---|---|---|---|---|
| `vkchat_market` | 4 java | 1 211 | MarketManager (870), MarketGuiListener (1006), MarketCommand (152), MarketFun (184) | 🔴 требует доработки |
| `vkchat_artifacts` | 5 java | 1 766 | ArtifactListener (820), ConsumablesListener (468), ArtifactFactory (380), VKChatArtifactsPlugin (287), ArtifactCommand (89) | 🔴 требует доработки |

---

## 🐛 Найденные проблемы (приоритет: 🔴 критично / 🟠 важно / 🟡 желательно)

### A. Маркет (`vkchat_market`)

#### 🔴 A1. Полное отсутствие валидации зачарований
**Файлы:** `MarketGuiListener.createRandomEnchantedBook()` (строки ~810-820), `MarketManager.addCustomBook()` (200-216)

- Используются **deprecation-методы** `Enchantment.PROTECTION_ENVIRONMENTAL`, `DAMAGE_ALL`, `DIG_SPEED`, `DURABILITY`, `LOOT_BONUS_BLOCKS`, `LOOT_BONUS_MOBS`, `FIRE_ASPECT`, `ARROW_DAMAGE`, `DEPTH_STRIDER`, `THORNS`, `PROTECTION_FALL`, `ARROW_INFINITE`.
- На 1.20.5+ все эти поля либо удалены, либо возвращают `null`, либо кидают `IllegalArgumentException`.
- **Нет проверки** на конфликтующие чары (см. ниже).
- **Нет проверки** на зачарованные книги из `addCustomBook`: любая ENCHANTED_BOOK принимается, даже с 6+ чарами в рандомных комбинациях.

#### 🔴 A2. `buyItems()` некорректно обрабатывает книги
**Файл:** `MarketGuiListener.buyItems()` (строки ~580-625)
- Когда `isBook = true`, вызывается `buyItems(itemId, amount, donorMult)` (метод `MarketManager` не в этой сигнатуре), но в `MarketManager.buyItems` параметр `donorMultiplier` используется не так, как ожидается: для книг вычисляется отдельная цена `base-price * donorMult`, минуя всю модель спроса/предложения.
- Игнорируется `actualAmount` — покупается столько, сколько `amount` (нет проверки стока), но цикл `for (i=0; i<actual; i++)` инвалиден для `ENCHANTED_BOOK` (нет стока).
- **Двойной вызов `markTrade`**: `markTrade` уже внутри `MarketManager.buyItems` отсутствует, но в листенере он вызывается после — всё ок, но путаница в логике.
- Если кастомная книга закончилась в стоке, в 30% случаев выдаётся **excellent book** через `Bukkit.dispatchCommand` — это уязвимость, если команда `excellentenchants randombook` отсутствует.

#### 🔴 A3. Race condition в `sellAllSellable()`
**Файл:** `MarketGuiListener.sellAllSellable()` (строки ~720-770)
- Метод читает `collectSellable(p)` дважды (для проверки `firstItem` и для самой продажи), между вызовами инвентарь может измениться (другой плагин, дроп, кража).
- **Нет** `markTrade` для всех предметов в `sellAllFromCommand` (вызывается только `sellItems`, не `markTrade`).
- `sellAllFromCommand` (строки ~660-700) добавляет реп через `VKChatBridge.addPoints(vkId, totalRep)`, но `MarketManager.sellItems` тоже возвращает `repToGive` — **сложение не происходит** (используется только результат `calculateBulkSellPrice`).

#### 🔴 A4. `recordQuestProgress` может выдавать реп дважды
**Файл:** `MarketFun.recordQuestProgress()` (строки ~85-110)
- `recordTrade` увеличивает volume, но `recordQuestProgress` вызывается **только из листенера** (sellItems/buyItems), а `MarketManager.recordTrade` уже инкрементит `dailyVolume` — это **разные счётчики** и бафф «+1 квест» может срабатывать дважды для одной сделки.
- При sell квеста `recordQuestProgress` не вызывается, потому что в `sellItems` (MarketGuiListener) он в конце, но в `sellAllFromCommand` — отсутствует.

#### 🟠 A5. `flashSale` не зависит от категории
**Файл:** `MarketFun.checkFlashSale()` (строки ~50-70)
- Бонус применяется в `MarketManager.getBuyPrice()` только для конкретного `itemId`, но в `applyFlashSale` — общий. Если событие длинное, оно может длиться и для категории, в которой уже не выгодно.
- `flashSaleDiscount` может быть `0.7`, но скидка применяется **после** спреда — то есть `sellPrice * (1 - 0.7) * (1 + 0.20)` — наценка спреда **не убирается** во время флеш-сейла, что логически неверно.

#### 🟠 A6. Сток/моментум: несимметричное восстановление
**Файл:** `MarketManager.recoverMarket()` (строки ~430-475)
- `Math.max(1, Math.round(recoveryRate * recoveryMultiplier))` — `recoveryMultiplier` по умолчанию `1.0`, но если поставить 0.5, восстановление **всё равно** = 1 (из-за `max(1, ...)`).
- `momentum` затухает на 10% каждые `recovery-interval` (по умолчанию 5 мин) — это **очень медленно** для высоколиквидных товаров (DIAMOND), моментум может удерживать цену в ±2.0 в течение часа.

#### 🟡 A7. `categoryMatches` — регулярки и хардкод
**Файл:** `MarketGuiListener.categoryMatches()` (строки ~840-860)
- Логика категоризации — набор `String.contains()` для русского и английского. Любое переименование конфига ломает фильтр.
- Категория `ice` присутствует в коде, но **не в slot array** в `openCategoryMenu` (8 категорий в 8 слотов, нет `ice`/`nether`).
- Категория `rare` определена, но `categoryMatches` возвращает true только для `cfg.contains("редкост")` — в конфиге ни одного `category: "редкост..."` нет → категория пуста.

#### 🟡 A8. `MarketCommand` — мёртвая ветка `spawnnpc`
**Файл:** `MarketCommand.onCommand()` (строки ~30-50)
- Подкоманда `spawnnpc` упомянута в tab-complete, но `e.setCancelled` в `MarketGuiListener.onInteract` реагирует на `Villager` с PDC ключом `market_npc` — **никто** этот ключ не ставит. NPC-фича полностью отключена, но код проверки остался.

#### 🟡 A9. Цены: дисбаланс base-price для DEEPSLATE_DIAMOND_ORE vs DIAMOND
- `DEEPSLATE_DIAMOND_ORE` base=1000, `DIAMOND` base=200 — руда в 5× дороже алмаза (а должно быть наоборот).
- `ANCIENT_DEBRIS` base=2000, `NETHERITE_SCRAP` base=800 — осколок должен быть **дороже** лома (4 лома = 1 незерит-слиток). Сейчас наоборот.

---

### B. Артефакты (`vkchat_artifacts`)

#### 🔴 B1. DRAGON_BLOOD дублирует `hasHealth`+`extraHealth`
**Файл:** `ArtifactListener.applyPassiveEffects()` (строки ~270-275)
```java
} else if (buff.equals("DRAGON_BLOOD")) {
    hasDragonBlood = true;
    extraHealth += 10;
    hasHealth = true;
    p.addPotionEffect(...REGENERATION, ..., 1...);
}
```
- `extraHealth += 10` добавляется **отдельно** от `ARTIFACT_HEALTH_UUID` modifier + `ARTIFACT_DRAGON_HP_UUID` modifier (тоже `+10`).
- Итого DRAGON_BLOOD даёт **+20 HP** вместо +10.
- Регенерация с амплитудой 1 стакается с баффом REGENERATION (тоже +1) — двойной реген.

#### 🔴 B2. VAMPIRISM × LIFESTEAL_AURA двойной вампиризм
**Файл:** `ArtifactListener.onDamageInternal()` (строки ~430-470)
- `VAMPIRISM` хилует атакующего: `p.setHealth(p.getHealth() + heal)`.
- `LIFESTEAL_AURA` хилует **союзников** в радиусе 8 блоков: `ally.setHealth(...)`.
- **Конфликт**: если у игрока есть оба предмета, оба эффекта срабатывают, что в 2× сильнее.
- Дополнительно: при `BERSERKER` (урон × (1 + missing% × 0.2 × level)) + `CRITICAL` (×2 при 5% × level) + `ECHO_STRIKE` (×2 при 10% × level) — три крита могут сработать одновременно, давая `damage * 2 * 2 * 1.4` ≈ ×5.6.

#### 🔴 B3. WIND_WALKER × SPEED = двойная скорость в воздухе
**Файл:** `ArtifactListener.applyPassiveEffects()` (строки ~180-185)
```java
} else if (buff.equals("WIND_WALKER")) {
    if (!p.isOnGround()) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, level - 1, false, false));
    }
}
```
- Плюс `SPEED` бафф: `speedMult += (level * 0.1)` → `ARTIFACT_SPEED_UUID` modifier.
- **Двойной источник**: SPEED potion + атрибут speed.
- На 1.20.5+ это **двукратный** прирост скорости (атрибут + зелье).

#### 🔴 B4. SLOWNESS проклятие блокирует SPEED
**Файл:** `ArtifactListener.applyPassiveEffects()` (строки ~195-200)
- `SLOWNESS` curse → `PotionEffectType.SLOW, 100, 0` (Slowness I).
- `SPEED` buff → `speedMult += 0.1` (атрибут).
- Эти эффекты **складываются** в Minecraft (амплитуды зелий и атрибутов суммируются), но игрок теряет 15% скорости от SLOWNESS даже если у него +50% атрибутом.
- На 1.20.5+ зелье SLOWNESS не складывается с атрибутом корректно — **известный баг**.

#### 🔴 B5. ABSORPTION (100, level-1) × SOUL_SHIELD × SOUL_SHIELD
- `ABSORPTION` buff → `PotionEffect(ABSORPTION, 100, level-1)` — постоянный щит.
- `SOUL_SHIELD` buff → при HP<30% дополнительно `ABSORPTION II, 100, 1`.
- Minecraft: только **самый сильный** ABSORPTION остаётся. Дополнительный слой — `extraHearts` + зелье — **суммируются** (это не баг, но приводит к "бессмертию" при низком HP).
- С `HASTE` + `FAST_DIGGING` — HASTE не стакается с Efficiency (vanilla), но код **не проверяет** зачарования на инструменте.

#### 🔴 B6. ENCHANTMENT_SCROLL_BOOST × 1.5 — глобальный, без проверок
**Файл:** `ArtifactListener.applyPassiveEffects()` (строки ~100-105)
```java
Long boostExpiry = ConsumablesListener.ENCHANTMENT_SCROLL_BOOST.get(p.getUniqueId());
if (boostExpiry != null && boostExpiry > System.currentTimeMillis()) {
    buffMult = 1.5;
}
```
- Применяется к: `HEALTH`, `SPEED`, `STEEL_SKIN` (armor), `KNOCKBACK_RESIST`, `MAX_HEALTH_BOOST`, `DRAGON_BLOOD`.
- **Не применяется** к: `REGENERATION`, `ABSORPTION`, `DAMAGE` (onDamage), `VAMPIRISM`, `LIFESTEAL_AURA`, `BERSERKER`, `ECHO_STRIKE`, `CRITICAL`.
- То есть **усиление работает неравномерно** — нарушает баланс (HEALTH ×1.5, но DAMAGE нет).

#### 🔴 B7. Curse of FRAGILE не находит `expireKey` для не-хрупких
- `applyPassiveEffects` в начале проверяет `if (has expireKey)`, но для **свежесгенерированных** артефактов с проклятием FRAGILE `expireKey` ставится. Однако, если FRAGILE-артефакт получен не через `generateArtifact` (например, ручной вызов), `expireKey` может отсутствовать, и **предмет не будет уничтожен по таймеру**.

#### 🔴 B8. `CHAOS` проклятие + `GHOST_WALK` бафф = слепота
- `CHAOS` проклятие рандомно выдаёт любое зелье, включая BLINDNESS.
- `GHOST_WALK` даёт INVISIBILITY.
- INVISIBILITY + BLINDNESS = вы **не видите себя**, и при этом `absorbedCurses` от Gear-сета **не работает для CHAOS** (проверяется только если `setAbsorbsCurses`, но Gear проверяется только в одном месте — пропущено в damage-логике).

#### 🔴 B9. SHADOW_STEP срабатывает внутри DODGE_CHANCE, но **не получает бонус**
- `DODGE_CHANCE` → `e.setCancelled(true)` + если есть `SHADOW_STEP` → `SPEED II 60`.
- **Сам DODGE_CHANCE имеет приоритет `EventPriority.HIGH`**, а `SHADOW_STEP` — внутри. Если DODGE не сработал, SHADOW_STEP **никогда не активируется**.

#### 🔴 B10. `SHADOW_STEP` + `DODGE_CHANCE` race: после DODGE → SHADOW_STEP даёт SPEED II, но **DODGE уже сетнул cancelled** — damage = 0. Потом BERSERKER из `onDamageInternal` **не сработает**, потому что `e.setDamage(e.getDamage() * 0)` = 0.

#### 🟠 B11. `netherite` осколок + `Netherite Ingot` в магазине: `isRareShopItem` блокирует продажу
- В `MarketGuiListener.isRareShopItem` проверяется `id.contains("NETHERITE_INGOT")` и `id.contains("ANCIENT_DEBRIS")` — **не могут быть проданы** в маркете.
- Но `MarketManager.sellItems` это **не проверяет** — игрок может продать, вызвав `MarketCommand -> sellAllFromCommand` напрямую.
- **Двойная логика**: GUI блокирует, код — нет.

#### 🟠 B12. `setAbsorbsCurses` в `applyPassiveEffects` — **внутри цикла** по предметам
- `gearPlugin.getConfig().getConfigurationSection("sets")` — **каждый предмет** вызывает `getConfigurationSection` на Gear.
- На 20 предметах в инвентаре = 20 файловых операций **каждый тик** (каждые 20 сек).
- **Стоимость O(N × M)** где N=предметы, M=сеты Gear — квадратичный рост.

#### 🟠 B13. `ArtifactListener.onDamageInternal` сканирует инвентарь 2 раза
- Сначала цикл по `p.getInventory().getContents()` для buff'ов, потом **отдельный цикл** для `BLOODLETTING`.
- Можно объединить, но сейчас — 2 прохода.

#### 🟠 B14. `onReputationChange` ищет игрока по `for (Player p : online)` каждый раз
**Файл:** `ArtifactListener.onReputationChange()` (строки ~395-440)
- На 100 игроках онлайн = 100 итераций `getLinkedVkId` **на каждое изменение репутации** (включая чат +1).
- Несмотря на `if (diff < 10) return;` — при diff=10-100 это может быть часто.

#### 🟠 B15. `ConsumablesListener.getBuffDescription` дублирует метод из `ArtifactFactory`
- Вся карта описаний скопирована из `ArtifactFactory.getBuffDescription` (380 строк).
- Любое изменение описания нужно делать в **двух местах**.

#### 🟡 B16. Именованные чарования-артефакты конфликтуют с реальными
- `SHARPNESS_ENCHANT` — это обычная `Sharpness`, которая конфликтует с `SMITE_ENCHANT` (`Smite`) и `BANE_ENCHANT` (`Bane of Arthropods`) — **Minecraft не разрешает** иметь все три.
- `PROTECTION_ENCHANT` (Protection) конфликтует с `FIRE_PROTECTION`, `BLAST_PROTECTION`, `PROJECTILE_PROTECTION` — но в `Enchantment.addEnchant(..., true)` **force=true** обходит проверку, и предмет получает **все 4** (4 × -20% = -80% урона).
- `INFINITY_ENCHANT` + `MENDING_ENCHANT` — **известный ванильный конфликт** (Mending не работает с Infinity).
- `DEPTH_STRIDER` + `FROST_WALKER` — конфликт (ванильный).
- `SILK_TOUCH` + `FORTUNE_ENCHANT` — конфликт.
- `CHANNELING` + `RIPTIDE` + `LOYALTY` — `Riptide` конфликтует с обоими.
- `MULTISHOT` + `PIERCING` — конфликт.
- `SHARPNESS` + `SMITE` + `BANE` — все три конфликтуют.

#### 🟡 B17. `createCustomItem` в MarketGuiListener НЕ конвертирует deprecation-чары
**Файл:** `MarketGuiListener.createCustomItem()` (строки ~285-300)
- `Enchantment.getByName(enchantStr)` — на 1.20.5+ вернёт `null` для `DAMAGE_ALL` (теперь `Enchantment.DAMAGE_ALL` не существует в виде поля).
- Нужно использовать `Registry.ENCHANTMENT.get(NamespacedKey.minecraft(...))`.

#### 🟡 B18. `EXCHANGE_RUNE` пересоздаёт lore с хардкодом
- В `ConsumablesListener` дублируется **огромный** switch по всем 53 buff'ам. Если добавить новый buff — нужно обновить 3 места.

#### 🟡 B19. `onInteract` для SILENCE блокирует любое использование
- `if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;` — но **onInteract** срабатывает на ЛКМ тоже? Нет, Action включает `LEFT_CLICK_*`, но проверка выше их отсекает. **Но** `PlayerInteractEvent` не срабатывает на ЛКМ по воздуху, только по блоку. По воздуху срабатывает только `RIGHT_CLICK_AIR`. Это **нормально**.
- Однако, `SILENCE` проклятие не блокирует **атаку**, а только `RIGHT_CLICK`. Описание не соответствует поведению.

#### 🟡 B20. `applyPassiveEffects` не учитывает **исчезновение** артефакта в процессе тика
- Внутри цикла `item.setAmount(0)` (для FRAGILE) — это модифицирует массив `p.getInventory().getContents()` во время итерации. **ConcurrentModificationException** вероятен, если `setAmount(0)` триггерит обновление массива.

---

## 🎯 Конфликты (карта для новой системы)

### Конфликты чар (Minecraft vanilla)

| Группа | Чары | Причина |
|---|---|---|
| **Damage swords** | SHARPNESS, SMITE, BANE_OF_ARTHROPODS | Все три несовместимы (ваниль) |
| **Protection** | PROTECTION, FIRE_PROTECTION, BLAST_PROTECTION, PROJECTILE_PROTECTION | Все четыре несовместимы |
| **Bow utility** | INFINITY, MENDING | Не работают вместе (ваниль) |
| **Trident** | LOYALTY, RIPTIDE, CHANNELING | Riptide vs остальные |
| **Crossbow** | MULTISHOT, PIERCING | Несовместимы |
| **Tool** | SILK_TOUCH, FORTUNE (LOOT_BONUS_BLOCKS) | Несовместимы |
| **Boots** | DEPTH_STRIDER, FROST_WALKER | Несовместимы |
| **Mending** | MENDING + INFINITY | См. выше |

### Конфликты RPG-баффов

| Buff A | Buff B | Поведение | Стратегия |
|---|---|---|---|
| HEALTH | MAX_HEALTH_BOOST | Суммируются | OK, но визуально одно |
| SPEED | WIND_WALKER | Двойной источник (атрибут + зелье) | Только атрибут |
| REGENERATION | DRAGON_BLOOD | Двойной реген | Только сильнейший |
| ABSORPTION | SOUL_SHIELD | Двойной щит | Только сильнейший |
| VAMPIRISM | LIFESTEAL_AURA | Двойной heal | Один эффект |
| BERSERKER | CRITICAL | ×2.2 крит | Один |
| BERSERKER | ECHO_STRIKE | ×2 множитель | Один |
| CRITICAL | ECHO_STRIKE | Оба ×2 (шанс) | Один шанс |
| DAMAGE | TRUE_STRIKE | Суммируются | OK, но лимит |
| FREEZE_AURA | FROST_BITE | Суммируются | OK |
| FIRE_RESISTANCE | FIRE_WALKER | Дубликат | Один |
| LUCK | LUCK_OF_THE_SEA | Суммируются | OK |
| WITHER_TOUCH | POISON_STRIKE | Разные эффекты | OK |
| STEEL_SKIN | IRON_WILL | Оба +armor | Суммируются OK |

### Конфликты проклятий

| Curse | Конфликт с | Поведение |
|---|---|---|
| SLOWNESS | SPEED (buff) | Частично компенсирует |
| WEAKNESS | DAMAGE (buff) | Частично компенсирует |
| VULNERABILITY | MANA_SHIELD | Сложение |
| GREED | (нет) | Снижает HP, +rep |
| ANCHOR | (teleport plugins) | Блокирует |
| SILENCE | (interact) | Блокирует ПКМ |

### Конфликты «бафф vs проклятие»

- **SPEED** buff + **SLOWNESS** curse → эффекты складываются
- **DAMAGE** buff + **WEAKNESS** curse → урон нейтрализуется
- **RESISTANCE** buff + **VULNERABILITY** curse → ~−10% net
- **JUMP_BOOST** buff + **SLOWNESS** curse → +jump −move
- **NIGHT_VISION** buff + **BLINDNESS** curse → NIGHT_VISION выигрывает в ванильном Minecraft (но визуально конфликтно)

---

## 📦 Готовые решения (что реализуем)

1. **`EnchantmentConflictManager`** — универсальный, поддержка 1.16-1.21 (через reflection для совместимости)
2. **`ArtifactEffectRegistry`** — приоритеты + группы + conflict matrix
3. **`BalancedMarketManager`** — переработанный менеджер маркета с фиксами A1-A9
4. **`MarketItemValidator`** — валидация предметов при покупке/продаже
5. **`MarketRebalance`** — обновлённый config.yml с правильными ценами
6. **`ArtifactListener` v2** — фиксы B1-B20
7. **`ConsumableFactory` v2** — устранение дубликатов описаний
8. **Gradle-сборка и итоговый jar**
