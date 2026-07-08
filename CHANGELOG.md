# CHANGELOG — VKChat Ultimate / CHRDK REBORN

## v3.2.0 — 8 Июля 2026 — Gear/Artifacts/Runes Full Refactor

### 🔴 10 ИСПРАВЛЕНИЙ GEAR/ARTIFACTS/RUNES (GEAR_FIX)

| # | Критичность | Исправление |
|---|------------|-------------|
| 1 | 🔴 HIGH | DonateStatusResolver заменяет захардкоженный if-else donate-скидки в GearManager.getDonateDiscount() |
| 2 | 🔴 HIGH | SetBonusManager заменяет 200+ строк хардкода в GearManager.checkSetBonus() — бонусы настраиваются из config.yml |
| 3 | 🔴 HIGH | ForgeLogger вынесен из ForgeCommand — SRP, структурированные логи с типами операций |
| 4 | 🔴 HIGH | RuneRegistry заменяет 30+ захардкоженных маппингов getEnchantIdByName() в RuneListener |
| 5 | 🟡 MEDIUM | takeVkReputation теперь поддерживает PassManager/локальную репутацию для проходочников |
| 6 | 🟡 MEDIUM | BuffEffectRegistry заменяет 50+ if-else цепочек в ArtifactListener.applyPassiveEffects() |
| 7 | 🟡 MEDIUM | RuneMarketManager загружает базовые цены из RuneRegistry/config вместо хардкода setupBases() |
| 8 | 🟡 MEDIUM | ArtifactFactory использует BuffEffectRegistry для описания проклятий вместо хардкода |
| 9 | 🟢 LOW | VKChatArtifactsPlugin инициализирует BuffEffectRegistry в onEnable() |
| 10 | 🟢 LOW | GearManager.checkSetBonus() помечен как @deprecated — делегирует в SetBonusManager |

### 🟢 10 УЛУЧШЕНИЙ GEAR/ARTIFACTS/RUNES (GEAR_IMPROVE)

| # | Улучшение |
|---|-----------|
| 1 | DonateStatusResolver — единая точка проверки донат-статусов для ВСЕХ модулей (gear, chat, market и др.) |
| 2 | SetBonusManager: конфиг-управляемые бонусы с поддержкой upgrade-level-based масштабирования (effects, debuffs, effects-15, effects-20) |
| 3 | ForgeLogger: структурированные логи с типами операций (FUSION_SUCCESS, UPGRADE_FAIL и др.) |
| 4 | RuneRegistry: name→ID маппинг из конфига вместо хардкода + автоматическое определение категории (weapon/armor/tool) |
| 5 | BuffEffectRegistry: типизированная система баффов/проклятий с категориями (POTION, ATTRIBUTE, SPECIAL, ON_ATTACK, ON_DAMAGE) |
| 6 | RuneMarketManager: цены рун из config.yml через RuneRegistry — без пересборки плагина |
| 7 | ArtifactFactory.getCurseLore() делегирует в BuffEffectRegistry с fallback на legacy switch |
| 8 | Gear plugin.yml v3.2.0 + Artifacts plugin.yml v3.2.0 |
| 9 | Все новые классы имеют fallback на legacy-логику для обратной совместимости |
| 10 | Root build.gradle v3.2.0 |

### 🏗️ Новые файлы

| Файл | Назначение |
|------|-----------|
| `vkchat_gear/.../donate/DonateStatusResolver.java` | Централизованный резолвер донат-статусов (spark/flame/star/legend/overlord) |
| `vkchat_gear/.../forge/ForgeLogger.java` | Выделенный логгер операций кузни |
| `vkchat_gear/.../forge/SetBonusManager.java` | Конфиг-управляемая система сет-бонусов |
| `vkchat_gear/.../runes/RuneRegistry.java` | Конфиг-управляемый реестр рун |
| `vkchat_artifacts/.../effects/BuffEffectRegistry.java` | Конфиг-управляемый реестр баффов/проклятий артефактов |

### 📦 Обновлённые файлы

| Файл | Изменение |
|------|-----------|
| `GearManager.java` | getDonateDiscount() → DonateStatusResolver; takeVkReputation() + PassManager; checkSetBonus() @deprecated; isWearingSet() → SetBonusManager |
| `ForgeCommand.java` | log() делегирует в ForgeLogger вместо прямого FileWriter |
| `RuneListener.java` | getEnchantIdByName() → RuneRegistry с fallback |
| `RuneMarketManager.java` | setupBases() → RuneRegistry для базовых цен с fallback |
| `ArtifactFactory.java` | getCurseLore() → BuffEffectRegistry с fallback |
| `VKChatGearPlugin.java` | Инициализация SetBonusManager, ForgeLogger, RuneRegistry |
| `VKChatArtifactsPlugin.java` | Инициализация BuffEffectRegistry, геттер |
| `vkchat_gear/plugin.yml` | v3.2.0 |
| `vkchat_gear/build.gradle` | v3.2.0 |
| `vkchat_artifacts/plugin.yml` | v3.2.0 |
| `vkchat_artifacts/build.gradle` | v3.2.0 |
| `build.gradle` | v3.2.0 |

---


## v3.1.0 — 8 Июля 2026 — Pass System Full Refactor

### 🔴 10 ИСПРАВЛЕНИЙ ПРОХОДКИ (PASS_FIX)

| # | Критичность | Исправление |
|---|------------|-------------|
| 1 | 🔴 HIGH | Хранение проходок по UUID вместо имён (было `Set<String>` → `Map<UUID, PassHolder>`) |
| 2 | 🔴 HIGH | Проверка истечения проходки при входе игрока (раньше только для донат-статусов) |
| 3 | 🔴 HIGH | Проходка автоматически удаляется при получении донат-статуса (раньше сосуществовали) |
| 4 | 🟡 MEDIUM | Валидация `passHolders` при загрузке — синхронизация с LuckPerms правами |
| 5 | 🟡 MEDIUM | Отдельная длительность проходки `pass.duration-days` (не `donation-duration-days`) |
| 6 | 🟡 MEDIUM | Пропуск выдачи проходки если игрок уже привязал ВК или имеет донат-статус |
| 7 | 🟡 MEDIUM | Локальная репутация для проходочников имеет настраиваемый лимит (`pass.local-rep-cap`) |
| 8 | 🟢 LOW | Очистка PDC `local_rep` при истечении/отзыве проходки (раньше оставался навсегда) |
| 9 | 🟢 LOW | Save-ahead при выдаче/удалении проходки (идемпотентность как в DonateManager) |
| 10 | 🔴 HIGH | Логика проходки вынесена из DonateManager в PassManager (SRP) |

### 🟢 10 УЛУЧШЕНИЙ ПРОХОДКИ (PASS_IMPROVE)

| # | Улучшение |
|---|-----------|
| 1 | Выделенный `PassManager` — Single Responsibility Principle |
| 2 | `PassHolder` record с метаданными (UUID, grantDate, expiryDate, source, amountPaid) |
| 3 | Миграция проходка → ВК: автоматический перенос локальной репутации при привязке ВК |
| 4 | `/pass` — отдельная команда для игроков (статус, репутация, покупка) |
| 5 | Grace-период: 1-3 дня после истечения проходки (настраиваемо) |
| 6 | Аналитика: куплено / активно / истекло / конвертировано в ВК + процент конверсии |
| 7 | Настраиваемые сообщения проходки из config.yml (8 новых шаблонов) |
| 8 | События Bukkit: `PassGrantEvent`, `PassExpireEvent`, `PassConvertEvent` |
| 9 | Автоочистка устаревших записей при загрузке + фоновая проверка каждые 5 мин |
| 10 | `/pass buy` — информационная команда о покупке проходки |

### 🏗️ Новые файлы

| Файл | Назначение |
|------|-----------|
| `pass/PassManager.java` | Централизованный менеджер проходок: выдача, продление, истечение, конвертация |
| `pass/PassCommand.java` | Команда /pass: info, rep, buy, list, give, remove, stats |
| `pass/event/PassGrantEvent.java` | Bukkit Event — выдача проходки |
| `pass/event/PassExpireEvent.java` | Bukkit Event — истечение проходки |
| `pass/event/PassConvertEvent.java` | Bukkit Event — конвертация проходки в ВК |

### 📦 Обновлённые файлы

| Файл | Изменение |
|------|-----------|
| `DonateManager.java` | Делегирование проходки в PassManager, `pass_holders` убран из donations.yml |
| `DonateCommand.java` | `/donate pass` делегирует в PassManager, `/donate status` показывает проходку |
| `VKChatDonatePlugin.java` | Инициализация PassManager, слушатель `VKPlayerLinkEvent` для автоконвертации |
| `config.yml` | v4: расширенный `pass` section (duration-days, grace-days, local-rep-cap, auto-convert), 8 новых сообщений |
| `plugin.yml` | v3.1.0, `/pass` команда, `vkchat.pass.use`, `vkchat.pass.admin` |
| `build.gradle` | v3.1.0 |

### 🔄 Миграция данных

- Старый формат `donations.yml` → `pass_holders: [name1, name2]` автоматически мигрируется в `pass_data.yml` по UUID
- `pass_data.yml` содержит: `holders.<uuid>.{last-name, grant-date, expiry-date, source, amount-paid}` + `stats.*`
- Поле `pass_holders` из `donations.yml` очищается при первом запуске v3.1

---

## v3.0.0 — 8 Июля 2026 — Donate Module Full Refactor

### 🔴 10 ИСПРАВЛЕНИЙ (FIX)

| # | Критичность | Исправление |
|---|------------|-------------|
| 1 | 🔴 HIGH | Все LP-операции через `LuckPermsHelper` (единая точка, нет размазывания) |
| 2 | 🔴 HIGH | LP API вместо `dispatchCommand("lp ...")` где возможно (atomic, нет race condition) |
| 3 | 🔴 HIGH | `getDaysLeft()` через чистый LuckPerms API (без reflection fallback) |
| 4 | 🟡 MEDIUM | HTTP-клиент вынесен в `DonatePayClient` (Single Responsibility) |
| 5 | 🟡 MEDIUM | Логирование HTTP-кода ошибки при polling (было молчание при 4xx/5xx) |
| 6 | 🟡 MEDIUM | Защита API-токена от утечки в логи (раньше мог попасть в exception) |
| 7 | 🟡 MEDIUM | Save-ahead `lastProcessedId` ПЕРЕД обработкой (идемпотентность) |
| 8 | 🟡 MEDIUM | `processingTxIds` — защита от параллельной обработки одного доната |
| 9 | 🟢 LOW | LP команды через UUID (а не имя) для онлайн-игроков |
| 10 | 🟢 LOW | `fundraiserCollected` сохраняется между рестартами в donations.yml |

### 🟢 10 УЛУЧШЕНИЙ (IMPROVE)

| # | Улучшение |
|---|-----------|
| 1 | StatusDef — getters вместо public полей (encapsulation) |
| 2 | Configurable donation duration (`donation-duration-days` в config.yml, не хардкод 30) |
| 3 | Логирование всех донатов в `donate.log` (файловый аудит) + `DonateLogEntry` |
| 4 | `/donate log` — просмотр последних 15 донатов для админов |
| 5 | Проверка истёкших статусов при входе (уведомление за 3 дня и при истечении) |
| 6 | `/donate upgrade` — информация о следующем статусе и сумме доплаты |
| 7 | Улучшенное извлечение ника: пробуем `sender` и `comment` из DonatePay |
| 8 | Настраиваемые сообщения в config.yml с переменными `{player}`, `{status}`, `{amount}` |
| 9 | Fundraiser: broadcast при достижении 100% цели |
| 10 | Статистика: `/donate stats` — общая сумма, количество донатеров, топ-5 |

### 🏗️ Новые файлы

| Файл | Назначение |
|------|-----------|
| `luckperms/LuckPermsHelper.java` | Централизованный LP API: setTempPermission, extendTempPermission, getDaysLeft |
| `api/DonatePayClient.java` | HTTP-клиент DonatePay: fetch, parse, error handling |

### 📦 Обновлённые файлы

| Файл | Изменение |
|------|-----------|
| `DonateManager.java` | Полная переработка: LP через LuckPermsHelper, логирование, конфигурируемые сообщения |
| `DonateCommand.java` | +upgrade, +log, +stats подкоманды |
| `VKChatDonatePlugin.java` | LuckPermsHelper.init(), checkExpiredStatus, статистика при запуске |
| `config.yml` | v3: +donation-duration-days, +pass section, +messages with variables |
| `plugin.yml` | v3.0.0, +vkchat.donate.use |
| `build.gradle` | v3.0.0 |

---

## v2.2.0 — 8 Июля 2026 — Market EE Integration

### 🧙 ExcellentEnchants Integration (Market v3.2.0)

- **NEW: `ExcellentEnchantsBridge`** — полноценный мост между маркетом и ExcellentEnchants
  - Кешированный реестр EE-зачарований с автообновлением (30 мин)
  - Получение EE-чар по раритетности (common/uncommon/rare/exotic/mythic)
  - Получение EE-чар по типу предмета (sword, pickaxe, armor...)
  - Создание EE-книг для рынка по ключу или раритетности
  - Взвешенная генерация (common=40%, uncommon=30%, rare=18%, exotic=9%, mythic=3%)
  - Graceful fallback: EE не установлен → ванильные книги
  - Проверка конфликтов чар через EnchantmentConflictManager
  - Читаемые имена и раритетность в lore книг

- **NEW: EE-книги в конфигурации рынка**
  - `EE_BOOK_RANDOM` — случайная EE-книга (500 реп)
  - `EE_BOOK_RARE` — редкая EE-книга (1500 реп)
  - `EE_BOOK_EXOTIC` — экзотическая EE-книга (5000 реп)
  - Параметр `ee-random: true` + `ee-min-rarity` для фильтрации

- **NEW: Конфигурация EE-интеграции** в config.yml
  - `excellent-enchants.enabled` — включение/выключение
  - `excellent-enchants.book-rarity-weights` — веса раритетов
  - `excellent-enchants.replace-vanilla-books` — замена ванильных книг на EE
  - `excellent-enchants.rarity-price-multipliers` — ценовые множители по раритету
  - `excellent-enchants.cache-refresh-interval` — интервал обновления кеша

- **IMPROVE: MarketItemFactory v2.0**
  - Все методы создания книг используют ExcellentEnchantsBridge
  - `createMarketItem()` показывает EE-раритетность и имя чар в lore
  - Римские цифры для уровней (I, II, III, IV, V...)
  - Единые донат-множители без дублирования

- **IMPROVE: MarketGuiListener**
  - Все вызовы `donorSellMultiplier()` / `donorBuyMultiplier()` → `MarketItemFactory.*`
  - Устаревшие `_deprecated` методы помечены к удалению
  - Покупка книг: EE → fallback на ванильные
  - `tryGiveExcellentBook()` заменён на ExcellentEnchantsBridge

- **FIX: plugin.yml** — добавлен `softdepend: [ExcellentEnchants]`

---

## v2.1.1 — 8 Июля 2026 — Рефакторинг

### 🏗️ Рефакторинг модуля Market (v3.1.0)

- **NEW: `PriceEngine`** — выделенный движок вычисления цен, спредов, множителей. Отделяет бизнес-логику от `MarketManager`
- **NEW: `TradeLogger`** — выделенный логгер транзакций и истории. Убирает логирование из God-класса
- **NEW: `MarketItemFactory`** — фабрика ItemStack для GUI. Устраняет дублирование в MarketGuiListener
- **FIX: `priceHistory`** теперь сохраняется на диск (раньше терялось при рестарте)
- **FIX: Устранено дублирование** `donorSellMultiplier()` / `donorSellMultiplierStatic()` — единый метод в MarketItemFactory
- **FIX: Устранены дублирующиеся импорты** в EnchantmentConflictManager
- **FIX: Объединены** `GROUP_DISPLAY_OVERRIDES` и `getGroupDisplayName()` — единый источник истины
- **IMPROVE: Добавлен `adjustStock()`** — атомарное изменение стока (для PriceEngine)
- **IMPROVE: Добавлены `getMarketCyclePhase()` / `getDailyTrend()`** — public getters для PriceEngine

### 💰 Рефакторинг модуля Donate (v2.1.0)

- **FIX: Идемпотентность** — `lastProcessedId` сохраняется ДО обработки доната (save-ahead), предотвращая дублирование при рестарте
- **FIX: Синхронизация** — добавлен `processingTxIds` Set для предотвращения параллельной обработки одного доната
- **FIX: LuckPerms API** — `getDaysLeft()` использует LuckPerms API вместо reflection (с fallback)
- **IMPROVE: Таблица подкоманд** — DonateCommand рефакторинг из лестницы if-else в Map<String, SubCommand>
- **IMPROVE: Динамическое создание групп** — setup больше не хардкодит имена групп, читает из config.yml
- **IMPROVE: `fundraiserCollected`** сохраняется в donations.yml между рестартами
- **IMPROVE: TabComplete** — полная поддержка для fundraiser и pass подкоманд
- **IMPROVE: Добавлено `vkchat.donate.use`** — отдельный permission для обычных игроков

---

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


### Исправления (все модули)

- **vkchat_teleport:** команды gateway/portal зарегистрированы (были объявлены, но не подключены)
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
- Команды: register, login, 2fa, vklink, rep, pay, nation, forge, runes, artifacts, market, jobs, events, donatepay, rtp, home, tpa, gateway, portal
