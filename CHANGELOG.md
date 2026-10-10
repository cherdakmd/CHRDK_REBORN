# CHANGELOG — VKChat Ultimate / CHRDK REBORN

## Unreleased — Миграция на Minecraft 26.3 (Purpur 26.3)

- Сервер: Minecraft **26.3** (Purpur 26.3, Java **25**). Было: Purpur 1.20.4.
- Сборка: Paper API `io.papermc.paper:paper-api:26.3.build.+` вместо Spigot API 1.16.5; Java toolchain 25, `options.release = 25`; Gradle wrapper 9.1.0.
- `plugin.yml` у всех плагинов: `api-version: "26.3"`.
- Переименованные API-константы (по таблице переименований Paper):
  - `Attribute.GENERIC_*` → `Attribute.*` (например `MAX_HEALTH`, `ATTACK_DAMAGE`, `MOVEMENT_SPEED`, `ARMOR`, `KNOCKBACK_RESISTANCE`);
  - `PotionEffectType`: `SLOW`→`SLOWNESS`, `FAST_DIGGING`→`HASTE`, `SLOW_DIGGING`→`MINING_FATIGUE`, `INCREASE_DAMAGE`→`STRENGTH`, `JUMP`→`JUMP_BOOST`, `CONFUSION`→`NAUSEA`, `DAMAGE_RESISTANCE`→`RESISTANCE`;
  - `Enchantment`: `DAMAGE_ALL`→`SHARPNESS`, `DURABILITY`→`UNBREAKING`, `DIG_SPEED`→`EFFICIENCY`, `LOOT_BONUS_BLOCKS`→`FORTUNE`, `LOOT_BONUS_MOBS`→`LOOTING`, `ARROW_*`→`POWER/PUNCH/FLAME/INFINITY`, `PROTECTION_*`→`PROTECTION/FIRE_PROTECTION/PROJECTILE_PROTECTION`;
  - `Particle`: `REDSTONE`→`DUST`, `BLOCK_CRACK`/`BLOCK_DUST`→`BLOCK`, `SNOW_SHOVEL`/`SNOWBALL`→`ITEM_SNOWBALL`, `SPELL_WITCH`→`WITCH`, `SMOKE_NORMAL`→`SMOKE`, `TOTEM`→`TOTEM_OF_UNDYING`, `TOWN_AURA`→`MYCELIUM` и другие;
  - `Material.SCUTE` → `Material.TURTLE_SCUTE`.
- Новый класс `ru.example.vkchat.util.BukkitNames`: строковые имена эффектов и зачарований из конфигов (`FAST_DIGGING;0`, `DAMAGE_ALL;1` и т.п.) принимаются и в старом, и в новом виде.
- Внимание: `api-version` 26.3 ставит плагин на новые имена. Старые имена, которые считываются НЕ через `BukkitNames` (например, частицы из конфига), нужно заменить вручную.

## v2.1.0 — Июнь 2026

### VKChatGear — Mythical Sets + Ancient Crystal + Max +25

**5 Мифических наборов:**
| Набон | Бонусы |
|---|---|
| **Костяной Доспех** | 50% шанс免疫 от смертельного урона + Regeneration II на 5 сек |
| **Тень Клинка** | Каждый 3-й удар крит + невидимость 3 сек после убийства |
| **Тлеющая Корона** | Огонь nearby врагов 4 сек + +50% к урону от огня |
| **Туман Чумы** | Отравление nearby врагов + Slowness II |
| **Звёздная Ковка** | Полная защита + Speed II + урон от неба мобам |

**Ancient Crystal (4-й тир кристаллов):**
- Материал: `HEART_OF_THE_SEA`
- Диапазон: +20–+25
- Шанс успеха: 25%
- Шанс даунгрейда: 40%
- Шанс уничтожения: 10%

**Максимальная заточка:** +25 (было +20)

---

### VKChatMobs — Light Element + Hunter Archetype + Mob Storm + Scalable Drops

**Стихия Света (Light):**
- +8 урона нежити (зомби, скелеты, визеры)
- CONFUSION 2 сек при попадании
- Частицы END_ROD

**Архетип Охотника (Hunter):**
- SLOW 3 сек + BLINDNESS 2 сек при атаке
- Ловушки: частицы smoke

**MobStormManager — Мировое событие:**
- 50 мобов в 3 волны за 30 секунд
- 10% шанс при убийстве мини-босса
- Оповещение всей нации

**ELEMENTAL контракт:**
- Убить 20 мобов случайной стихии
- Доступен после 5 завершённых контрактов

**Дроп Ancient Crystal:**
- HEART_OF_THE_SEA с rank 9+ (~0.83%)

**Масштабируемый дроп рун:**
- `5% + 3% × ранг`, макс 50%

---

### VKChatEvents — 3 новых катаклизма + Автоспавн + Защита наций

**3 новых катаклизма:**
| Катаклизм | Описание |
|---|---|
| **Fog Shadows** | Тени из тумана атакуют игроков |
| **Plasma Storm** | Плазменные разряды наносят урон |
| **Gravity Anomaly** | Гравитация притягивает/отталкивает игроков |

**Конфигурируемые параметры босса:**
- Имя, тип, HP, лут — настраиваются в `wrath.boss.*`

**Конфигурируемые параметры катаклизмов:**
- Длительность, интервал тиков, шансы — в `wrath.cataclysms.*`

**Автоспавн катаклизмов:**
- Каждые 5 минут, 8% шанс
- Катаклизм происходит ВОКРУГ случайного онлайн-игрока (радиус 64 блока)
- Взвешенный выбор типа по конфигу
- Личное уведомление игроку
- Все 16 типов катаклизмов

**Защита приватов наций:**
- `EntityExplodeEvent` — взрывы не ломают блоки в приватах
- `shouldAffectPlayer()` — все эффекты применяются только вне приватов
- Eclipse/Blood Moon/Fog Shadows — мобы не спавнятся в приватах

---

### VKChatNations — ClaimDefenseManager (3 ивента защиты)

**3 типа автоматических атак:**

| Ивент | Шанс | Описание | Длительность | Награда |
|---|---|---|---|---|
| **Рейд** | 40% | 20 мобов в 3 волны | 3 мин | +50 прочности |
| **Осада** | 30% | Босс 200 HP + 12 элитных миньонов | 5 мин | +100 прочности |
| **Диверсия** | 30% | 8 невидимых диверсантов | 2 мин | +30 прочности |

**Автоматический запуск:**
- Каждые 1 час проверка, 10% шанс
- Кулдаун 2 часа на нацию
- Средний интервал: ~10 часов

**Ручной запуск:**
- `/nation defend` — запускает случайный ивент в текущем привате

**Боссы и миньоны:**
- Босс осады: `WITHER_SKELETON` с 200 HP, зелья Скорости/Силы/Резиста
- Миньоны: кожаная броня фиолетового цвета
- Диверсанты: невидимость + Speed + Slow Falling

---

### Исправления (все модули)

- **vkchat_teleport:** команды gateway/portal зарегистрированы (были объявлены, но не подключены)
- **vkchat_offline:** null-safety для VKChatJobsPlugin.getInstance()
- **vkchat_starter:** softdepend на VKChatMobs и VKChatArtifacts
- **vkchat_core:** BloodMoonManager world теперь конфигурируется
- **vkchat_core:** QuestManager → `quest_progress.yml`, BountyManager → `bounties.yml`
- **vkchat_core:** MotdListener — все 16 типов катаклизмов
- **vkchat_mobs:** PlaceholderAPI удалён из build.gradle
- **vkchat_mobs:** shared `createSetFragment()` — убрана дупликация

---

## v2.0.5 — Июнь 2026

- Интеграция `VKLongPollManager` в `VKChatPlugin` для стабильности LongPoll
- Переработка `onEnable()`/`onDisable()` lifecycle

---

## v2.0.0 — Июнь 2026

### Major Features
- **Forge 2.0:** хаб `/forge`, слияние редкости, свитки, защита, логи
- **Offline 2.0:** кампания, психика, травмы, госпиталь, коллекции
- **Jobs 2.0:** ежедневки и специализации
- **Market 2.0:** тренды дня, история, ротация редкостей
- **Mobs/Events 2.0:** контракты, рейд-боссы, мировые угрозы
- **DonatePay:** 4 месячных статуса с LuckPerms-группами

### Nations
- 6 наций: Совет, КГБ, Волхвы, Культ, Русь, Опричнина
- Блочная система приватов (Малый/Средний/Большой)
- 5-уровневая прокачка приватов
- Лаборатория Мутаций (30 мутаций)

### Core
- VK интеграция, авторизация, 2FA
- Репутация ВК как валюта
- Модерация, предупреждения

### Commands
- Tab Complete для всех 13 командных классов
- Команды: register, login, 2fa, vklink, rep, pay, nation, forge, runes, artifacts, market, jobs, events, donatepay, stash, rtp, home, tpa, gateway, portal
