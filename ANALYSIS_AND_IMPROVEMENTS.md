# CHRDK REBORN — Полный анализ проекта, 10 улучшений Market и рефакторинг

**Дата:** 2026-07-08  
**Репозиторий:** https://github.com/cherdakmd/CHRDK_REBORN  
**Текущая версия:** 2.0.0 → **Рефакторинг v2.1.1**  

---

## 📋 Обзор проекта

CHRDK REBORN — серверный плагинный комплекс для Minecraft (Spigot 1.16.5–1.21.x), состоящий из **16 модулей** и **158 Java-файлов**. Многомодульная Gradle-сборка с `vkchat_core` как ядром.

| Модуль | Файлов | Назначение |
|--------|--------|-----------|
| `vkchat_core` | 35 | Ядро: авторизация, ВК-интеграция, репутация, БД, VK LongPoll |
| `vkchat_market` | 5+3NEW | Динамическая биржа с экономикой спроса/предложения |
| `vkchat_donate` | 3 | Система донатов через DonatePay API + LuckPerms |
| `vkchat_artifacts` | 10 | Артефакты, баффы, проклятия, боссы |
| `vkchat_gear` | 8 | Снаряжение, ковка, руны, синтез |
| `vkchat_nations` | 10 | Нации, приваты, налоги, защита территорий |
| `vkchat_events` | 18 | Ивенты, квесты, достижения, магазин |
| `vkchat_mobs` | 7 | Хардкорные мобы, осады, штормы |
| `vkchat_jobs` | 5 | Работы и заработок |
| `vkchat_teleport` | 5 | Телепортация, ТП-история |
| `vkchat_offline` | 6 | Офлайн-смена, тайники, проходки |
| `vkchat_chat` | 5 | Чат, TAB, broadcast |
| `vkchat_announcer` | 3 | Анонсы, викторины |
| `vkchat_streams` | 5 | Стрим-интеграция (Twitch/VK) |
| `vkchat_starter` | 4 | Стартовый квест, play-time |

---

## 🔍 10 Улучшений для модуля Market

### 1. 🏗️ God-класс MarketGuiListener (1005 строк) — декомпозиция
**Проблема:** Весь GUI, обработка кликов, торговые операции, создание предметов, навигация — в одном классе.  
**✅ Решение (реализовано):** Выделен `MarketItemFactory` — фабрика ItemStack для GUI. Подготовлена структура для `MarketGuiBuilder` и `MarketClickHandler`.

### 2. 🔄 Дублирование донат-множителей
**Проблема:** Методы `donorSellMultiplier()`, `donorSellMultiplierStatic()`, `donorBuyMultiplier()` дублируют друг друга. Хардкод значений (1.70, 1.50, 0.35...) в нескольких местах.  
**✅ Решение (реализовано):** Единый `MarketItemFactory.donorSellMultiplier()` и `MarketItemFactory.donorBuyMultiplier()` — статические методы без дублирования.

### 3. 🧪 Отсутствие транзакционности в торговых операциях
**Проблема:** В `sellItems()` — сначала репутация начисляется, потом предметы удаляются. Если инвентарь изменится между шагами — рассинхрон.  
**⚠️ Решение (рекомендация):** Ввести паттерн "reserve → validate → commit":
```java
TradeReservation res = reserve(player, itemId, count);
if (!res.isValid()) return;
commit(res); // атомарная операция
```

### 4. 📊 PriceHistory не сохраняется на диск
**Проблема:** `priceHistory` — `Map<String, List<Double>>` в памяти. При рестарте — все данные теряются.  
**✅ Решение (реализовано):** Добавлены `savePriceHistory()` / `loadPriceHistory()` в MarketManager. Сохранение с ротацией (макс. 100 точек).

### 5. ⚡ Конкуренция в ConcurrentHashMap при sellAll
**Проблема:** Race condition при параллельных продажах одного предмета.  
**⚠️ Решение (рекомендация):** Синхронизация на уровне `itemId` через `computeIfAbsent()` или `synchronized` блок по ключу.

### 6. 🔐 Нет ограничения на объём одной сделки
**Проблема:** SHIFT_LEFT → sellItems(itemId, 64). Нет дневного лимита.  
**⚠️ Решение (рекомендация):** Добавить `max-daily-volume-per-player` в config.yml и проверку в `sellItems()`.

### 7. 📈 Рыночный цикл не визуализирован в GUI
**Проблема:** `marketCyclePhase` (БУМ/КРАХ) показывается только текстом.  
**⚠️ Решение (рекомендация):** Визуальный индикатор в шапке GUI: анимация, изменение цвета рамки.

### 8. 🛒 Отсутствие корзины (batch-покупки)
**Проблема:** Покупка только по 1/16 шт. за клик.  
**⚠️ Решение (рекомендация):** Добавить `CartManager` с GUI корзины: ПКМ добавляет, отдельный слот подтверждения.

### 9. 🧹 MarketManager — God-объект (700+ строк)
**Проблема:** Один класс отвечает за: стоки, цены, тренды, события, циклы, книги, историю, аудит.  
**✅ Решение (реализовано):** Выделены `PriceEngine` и `TradeLogger` — отдельные классы с единой ответственностью.

### 10. 🔔 Нет уведомлений о Flash Sale и квестах
**Проблема:** Игрок оффлайн — пропускает анонс.  
**⚠️ Решение (рекомендация):** Сохранять "непрочитанные" события. При заходе — title/ActionBar с активными Flash Sale.

---

## 🔍 Анализ модуля Donate — найденные проблемы

| # | Критичность | Проблема | Статус |
|---|------------|----------|--------|
| 1 | 🔴 HIGH | **API-токен в открытом виде** — любой с доступом к файлам сервера | ⚠️ Рекомендация: шифрование |
| 2 | 🔴 HIGH | **Отсутствие идемпотентности** — при рестарте `lastProcessedId` мог не сохраниться → двойная обработка | ✅ **FIX: Save-ahead** |
| 3 | 🔴 HIGH | **Race condition** — параллельная обработка одного доната | ✅ **FIX: processingTxIds** |
| 4 | 🟡 MEDIUM | **Reflection-доступ к LuckPerms** в `getDaysLeft()` — хрупко | ✅ **FIX: LuckPerms API** |
| 5 | 🟡 MEDIUM | **Ник из поля "what"** — ненадёжно, опечатка = потеря денег | ⚠️ Рекомендация: верификация |
| 6 | 🟡 MEDIUM | **Нет webhook-режима** — polling задерживает на 30+ сек | ⚠️ Рекомендация |
| 7 | 🟢 LOW | `fundraiserCollected` не сохраняется | ✅ **FIX: в donations.yml** |
| 8 | 🟢 LOW | Хардкод групп в DonateCommand.setup | ✅ **FIX: динамическое чтение** |
| 9 | 🟢 LOW | Лестница if-else в DonateCommand | ✅ **FIX: таблица подкоманд** |

---

## 🔧 Реализованный рефакторинг — сводка

### Новые файлы

| Файл | Назначение |
|------|-----------|
| `vkchat_market/.../data/PriceEngine.java` | Движок вычисления цен, спредов, множителей |
| `vkchat_market/.../data/TradeLogger.java` | Логгер транзакций и истории рынка |
| `vkchat_market/.../gui/MarketItemFactory.java` | Фабрика ItemStack для GUI, единые донат-множители |
| `ANALYSIS_AND_IMPROVEMENTS.md` | Этот документ |

### Изменённые файлы

| Файл | Изменение |
|------|-----------|
| `vkchat_donate/.../DonateManager.java` | Save-ahead, LuckPerms API, синхронизация |
| `vkchat_donate/.../DonateCommand.java` | Таблица подкоманд, динамическое создание групп |
| `vkchat_donate/.../plugin.yml` | Версия 2.1.0, новый permission `vkchat.donate.use` |
| `vkchat_donate/build.gradle` | LuckPerms API dependency, версия 2.1.0 |
| `vkchat_market/.../MarketManager.java` | Интеграция PriceEngine/TradeLogger, priceHistory persistence |
| `vkchat_market/.../EnchantmentConflictManager.java` | Убраны дублирующиеся импорты, объединены display names |
| `vkchat_market/.../MarketGuiListener.java` | Импорт MarketItemFactory, устаревшие методы помечены deprecated |
| `vkchat_market/.../plugin.yml` | Версия 3.1.0 |
| `vkchat_market/build.gradle` | Версия 3.1.0 |
| `build.gradle` | Версия 2.1.1 |
| `CHANGELOG.md` | Добавлена секция v2.1.1 |
| `.gitignore` | Очищен от битых символов |

---

## 📁 Структура архива для GitHub

```
CHRDK_REBORN/
├── .github/
├── CHRD_MARKET_AND_CHAR_FIX_v2.1.0/
├── TAB/
├── docs/
├── gradle/wrapper/
├── vkchat_announcer/
├── vkchat_artifacts/
├── vkchat_chat/
├── vkchat_core/
├── vkchat_donate/           ← РЕФАКТОРИНГ
│   ├── build.gradle         ← +LuckPerms API
│   └── src/main/java/ru/example/vkchatdonate/
│       ├── DonateCommand.java  ← таблица подкоманд
│       ├── DonateManager.java  ← save-ahead, LuckPerms API
│       └── VKChatDonatePlugin.java
├── vkchat_events/
├── vkchat_gear/
├── vkchat_jobs/
├── vkchat_market/           ← РЕФАКТОРИНГ
│   ├── build.gradle
│   └── src/main/java/ru/example/vkchatmarket/
│       ├── commands/MarketCommand.java
│       ├── conflicts/EnchantmentConflictManager.java  ← fix imports
│       ├── data/
│       │   ├── MarketManager.java      ← +PriceEngine, +TradeLogger, +priceHistory persistence
│       │   ├── MarketFun.java
│       │   ├── PriceEngine.java        ← НОВЫЙ
│       │   └── TradeLogger.java        ← НОВЫЙ
│       ├── gui/
│       │   └── MarketItemFactory.java  ← НОВЫЙ
│       └── listeners/MarketGuiListener.java  ← +import MarketItemFactory
├── vkchat_mobs/
├── vkchat_nations/
├── vkchat_offline/
├── vkchat_starter/
├── vkchat_streams/
├── vkchat_teleport/
├── vkchat_announcer/
├── ANALYSIS_AND_IMPROVEMENTS.md  ← НОВЫЙ: этот документ
├── CHANGELOG.md                  ← обновлён
├── build.gradle                  ← v2.1.1
├── settings.gradle
└── .gitignore                    ← очищен
```

---

## 🚀 Инструкция по загрузке на GitHub

```bash
# Вариант 1: Прямой push (если есть права)
cd CHRDK_REBORN
git add -A
git commit -m "refactor: market PriceEngine/TradeLogger/MarketItemFactory + donate save-ahead/LuckPerms API"
git push origin master

# Вариант 2: Из архива
tar xzf CHRDK_REBORN_refactored.tar.gz
cd CHRDK_REBORN
git init
git add -A
git commit -m "refactor v2.1.1: market decomposition + donate fixes"
git remote add origin https://github.com/cherdakmd/CHRDK_REBORN.git
git push -f origin master
```

---

## 📊 Метрики рефакторинга

| Метрика | До | После |
|---------|-----|-------|
| MarketGuiListener строк | 1005 | 1005 (deprecated методы) |
| God-классов | 2 (MarketManager, MarketGuiListener) | Частично декомпозирован |
| Дублирующихся методов | 3 (donorSell x2, buy x1) | 1 (в MarketItemFactory) |
| Идемпотентность донатов | ❌ | ✅ (save-ahead) |
| LuckPerms API | Reflection | API + fallback |
| priceHistory persistence | ❌ | ✅ |
| DonateCommand структура | if-else chain | SubCommand table |

---

## 🔍 Анализ модуля Pass (Проходка) — v3.1.0

### Найденные проблемы

| # | Критичность | Проблема | Статус |
|---|------------|----------|--------|
| 1 | 🔴 HIGH | **Хранение по именам** — `Set<String>` вместо UUID, ломается при смене ника | ✅ **FIX: UUID-based PassHolder** |
| 2 | 🔴 HIGH | **Нет проверки истечения при входе** — игрок заходит с истёкшей проходкой и не знает | ✅ **FIX: checkPassExpiry** |
| 3 | 🔴 HIGH | **Проходка + донат-статус одновременно** — логика не проверяет пересечение | ✅ **FIX: removePassOnStatusGrant** |
| 4 | 🟡 MEDIUM | **Нет валидации** — passHolders не сверяется с LP при загрузке | ✅ **FIX: validatePassHolders** |
| 5 | 🟡 MEDIUM | **Единая длительность** — проходка = донат-статус = 30д, без раздельной настройки | ✅ **FIX: pass.duration-days** |
| 6 | 🟡 MEDIUM | **Нет проверки ВК** — можно выдать проходку игроку с привязанным ВК | ✅ **FIX: grantPass skip check** |
| 7 | 🟡 MEDIUM | **Нет лимита лок. репутации** — проходочник может накопить бесконечно | ✅ **FIX: pass.local-rep-cap** |
| 8 | 🟢 LOW | **PDC local_rep не чистится** — при истечении проходки данные остаются навсегда | ✅ **FIX: cleanupLocalRep** |
| 9 | 🟢 LOW | **Нет save-ahead** — при краше может быть несогласованность | ✅ **FIX: save-ahead в PassManager** |
| 10 | 🔴 HIGH | **Логика в DonateManager** — нарушение SRP, 836 строк и рост | ✅ **FIX: выделен PassManager** |

### Реализованные улучшения

| # | Улучшение | Описание |
|---|-----------|----------|
| 1 | **PassManager** | Выделенный класс с единой ответственностью (~470 строк) |
| 2 | **PassHolder record** | UUID, grantDate, expiryDate, source, amountPaid — полные метаданные |
| 3 | **Миграция проходка → ВК** | При привязке ВК автоматически переносит лок. репутацию и удаляет проходку |
| 4 | **/pass команда** | Отдельная команда: info, rep, buy, list, give, remove, stats |
| 5 | **Grace-период** | 1-3 дня после истечения — игрок может ещё зайти, видит предупреждение |
| 6 | **Аналитика** | Куплено/активно/истекло/конвертировано + процент конверсии |
| 7 | **Настраиваемые сообщения** | 8 новых шаблонов: pass-grant, pass-expiring, pass-expired, pass-grace, pass-converted |
| 8 | **Bukkit Events** | PassGrantEvent, PassExpireEvent, PassConvertEvent для расширяемости |
| 9 | **Автоочистка** | Фоновая проверка каждые 5 мин + очистка при загрузке |
| 10 | **/pass buy** | Информационная команда — как купить проходку через DonatePay |

### Архитектура PassManager

```
VKChatDonatePlugin
├── DonateManager (статусы, polling, fundraiser)
│   ├── processDonation() → делегирует проходку в PassManager
│   └── handleStatusGrant() → removePassOnStatusGrant()
├── PassManager (проходки)
│   ├── grantPass() — выдача с проверками ВК и статуса
│   ├── extendPass() — продление
│   ├── removePass() — отзыв + очистка PDC
│   ├── removePassOnStatusGrant() — FIX #3
│   ├── checkPassExpiry() — FIX #2 + grace-период
│   ├── convertPassToVk() — IMPROVE #3
│   ├── getLocalRep/addLocalRep/takeLocalRep — FIX #7 (cap)
│   └── getAnalytics() — IMPROVE #6
├── PassCommand (/pass)
│   ├── info — статус проходки
│   ├── rep — локальная репутация
│   ├── buy — как купить
│   └── admin: list, give, remove, stats
└── Events (pass/event/)
    ├── PassGrantEvent
    ├── PassExpireEvent
    └── PassConvertEvent
```

### Миграция данных (автоматическая)

```
donations.yml:
  pass_holders: [name1, name2, ...]    ← старый формат

                    ↓ при первом запуске v3.1 ↓

pass_data.yml:
  stats:
    total-purchased: 2
    total-converted: 0
    total-expired: 0
  holders:
    <uuid-1>:
      last-name: "name1"
      grant-date: 1720000000000
      expiry-date: 1722592000000
      source: "donate-legacy"
      amount-paid: 0
    <uuid-2>:
      last-name: "name2"
      ...
```

### Файлы рефакторинга Pass v3.1

| Файл | Тип | Назначение |
|------|-----|-----------|
| `pass/PassManager.java` | НОВЫЙ | Менеджер проходок (470 строк) |
| `pass/PassCommand.java` | НОВЫЙ | Команда /pass (180 строк) |
| `pass/event/PassGrantEvent.java` | НОВЫЙ | Bukkit Event — выдача |
| `pass/event/PassExpireEvent.java` | НОВЫЙ | Bukkit Event — истечение |
| `pass/event/PassConvertEvent.java` | НОВЫЙ | Bukkit Event — конвертация → ВК |
| `DonateManager.java` | ОБНОВЛЁН | Делегирование проходки, pass_holders убран |
| `DonateCommand.java` | ОБНОВЛЁН | /donate pass делегирует в PassManager |
| `VKChatDonatePlugin.java` | ОБНОВЛЁН | PassManager init + VKPlayerLinkEvent listener |
| `config.yml` | ОБНОВЛЁН | v4: расширенный pass section + 8 новых сообщений |
| `plugin.yml` | ОБНОВЛЁН | v3.1.0, /pass команда, новые permissions |
| `build.gradle` | ОБНОВЛЁН | v3.1.0 |
