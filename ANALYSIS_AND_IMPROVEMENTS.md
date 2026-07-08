# CHRDK REBORN — Анализ проекта, улучшения и рефакторинг

**Дата:** 2026-07-08  
**Версия:** 2.0.0  

---

## 📋 Обзор проекта

CHRDK REBORN — серверный плагинный комплекс для Minecraft (Spigot 1.16.5–1.21.x), состоящий из **16 модулей** и **158 Java-файлов**. Основные модули:

| Модуль | Назначение |
|--------|-----------|
| `vkchat_core` | Ядро: авторизация, ВК-интеграция, репутация, БД |
| `vkchat_market` | Динамическая биржа с экономикой |
| `vkchat_donate` | Система донатов через DonatePay |
| `vkchat_artifacts` | Артефакты, баффы, проклятия |
| `vkchat_gear` | Снаряжение, ковка, руны |
| `vkchat_nations` | Нации, приваты, налоги |
| `vkchat_events` | Ивенты, квесты, достижения |
| `vkchat_mobs` | Хардкорные мобы, осады |
| `vkchat_jobs` | Работы и заработок |
| `vkchat_teleport` | Телепортация, ТП-история |
| `vkchat_offline` | Офлайн-смена, тайники |
| `vkchat_chat` | Чат, таб, broadcast |
| `vkchat_announcer` | Анонсы, викторины |
| `vkchat_streams` | Стрим-интеграция |
| `vkchat_starter` | Стартовый квест, play-time |

---

## 🔍 10 Улучшений для модуля Market

### 1. 🏗️ God-класс MarketGuiListener (1005 строк) — декомпозиция
**Проблема:** Весь GUI, обработка кликов, торговые операции, создание предметов, навигация — в одном классе.  
**Решение:** Разделить на:
- `MarketGuiBuilder` — создание инвентаря, предметов, навигации
- `MarketClickHandler` — обработка кликов и торговых операций
- `MarketItemFactory` — фабрика предметов (createMarketItem, createLimitedItem и т.д.)

### 2. 🔄 Дублирование донат-множителей (donorSellMultiplier)
**Проблема:** Методы `donorSellMultiplier()`, `donorSellMultiplierStatic()`, `donorBuyMultiplier()` дублируют друг друга. Плюс хардкод значений (1.70, 1.50, 0.35...) в нескольких местах.  
**Решение:** Единый `DonorBonusResolver` через VKChatAPI/DonateManager, конфигурируемый из config.yml donate-модуля.

### 3. 🧪 Отсутствие транзакционности в торговых операциях
**Проблема:** В `sellItems()` — сначала репутация начисляется, потом предметы удаляются. Если инвентарь изменится между шагами — рассинхрон. В `buyItems()` — инвентарь пополняется до списания репутации.  
**Решение:** Ввести паттерн "reserve → validate → commit":
```java
// 1. Зарезервировать ресурсы
TradeReservation res = reserve(player, itemId, count);
// 2. Валидация
if (!res.isValid()) return;
// 3. Атомарный commit
commit(res);
```

### 4. 📊 PriceHistory не сохраняется на диск
**Проблема:** `priceHistory` — `Map<String, List<Double>>` в памяти. При рестарте — все данные теряются.  
**Решение:** Сохранять priceHistory в market_data.yml с ротацией (макс. 100 точек на предмет).

### 5. ⚡ Конкуренция в ConcurrentHashMap при sellAll
**Проблема:** `sellAllFromCommand()` и `sellAllSellable()` перебирают инвентарь синхронно, но `MarketManager` использует `ConcurrentHashMap` для stock. Race condition при параллельных продажах.  
**Решение:** Синхронизация на уровне `itemId` через `computeIfAbsent()` или `synchronized` блок по ключу.

### 6. 🔐 Нет ограничения на объём одной сделки
**Проблема:** Игрок может продать 64 стака за один клик (SHIFT_LEFT → sellItems(itemId, 64)). Нет дневного лимита.  
**Решение:** Добавить `max-daily-volume-per-player` в config.yml и проверку в `sellItems()`.

### 7. 📈 Рыночный цикл не отображается в GUI
**Проблема:** `marketCyclePhase` (БУМ/КРАХ/СТАБИЛЬНО) вычисляется, но показывается только текстом.  
**Решение:** Визуальный индикатор в шапке GUI: фейерверк при БУМе, туман при КРАХе. Изменение цвета рамки инвентаря.

### 8. 🛒 Отсутствие корзины (batch-покупки)
**Проблема:** Покупка только по 1/16 шт. за клик. Для массовых покупок — утомительно.  
**Решение:** Добавить `CartManager` с GUI корзины: ПКМ добавляет в корзину, отдельный слот подтверждения.

### 9. 🧹 MarketManager — God-объект (700+ строк)
**Проблема:** Один класс отвечает за: стоки, цены, тренды, события, циклы, книги, историю, аудит.  
**Решение:** Выделить:
- `PriceEngine` — вычисление цен, множителей, спредов
- `MarketEventScheduler` — события, циклы, ротация
- `TradeLogger` — логирование, история, аудит

### 10. 🔔 Нет уведомлений о Flash Sale и квестах
**Проблема:** `checkFlashSale()` только broadcast в чат. Если игрок оффлайн — пропустит.  
**Решение:** Сохранять "непрочитанные" события в player data. При заходе — показывать title/ActionBar с активными Flash Sale.

---

## 🔍 Анализ модуля Donate

### Найденные проблемы

| # | Критичность | Проблема |
|---|------------|----------|
| 1 | 🔴 HIGH | **API-токен в открытом виде в config.yml** — любой с доступом к файлам сервера может прочитать |
| 2 | 🔴 HIGH | **Отсутствие идемпотентности** — при рестарте во время обработки `lastProcessedId` может не сохраниться, донат обработается дважды |
| 3 | 🟡 MEDIUM | **Reflection-доступ к LuckPerms** в `getDaysLeft()` — хрупко, может сломаться при обновлении LP |
| 4 | 🟡 MEDIUM | **Ник из поля "what"** — ненадёжно, игрок может ошибиться, опечатка = потеря денег |
| 5 | 🟡 MEDIUM | **Нет webhook-режима** — только polling, что задерживает обработку на 30+ сек |
| 6 | 🟢 LOW | `fundraiserCollected` не сохраняется между рестартами |
| 7 | 🟢 LOW | Хардкод групп spark/flame/star/legend/overlord в DonateCommand.setup |

### Рекомендации для модуля Donate

1. **Шифрование токена** — хранить зашифрованным, расшифровывать в памяти
2. **Save lastProcessedId ДО обработки** — atomic write-ahead log
3. **LuckPerms API** — использовать `LuckPermsProvider.get()` вместо reflection
4. **Верификация ника** — проверять через Mojang API или позволять игроку привязать ник к DonatePay
5. **Webhook endpoint** — встроенный HTTP-сервер (Spark/Javalin) для мгновенной обработки
6. **Сохранять fundraiserCollected** в donations.yml

---

## 🔧 Рефакторинг (выполненные изменения)

### Структура рефакторинга

```
vkchat_market/
├── commands/          ← MarketCommand (без изменений)
├── conflicts/         ← EnchantmentConflictManager (убраны дубли imports)
├── data/
│   ├── MarketManager  ← Рефакторинг: вынесены PriceEngine, MarketEventScheduler
│   ├── MarketFun      ← Без изменений
│   ├── PriceEngine    ← НОВЫЙ: вычисление цен, спредов, множителей
│   └── TradeLogger    ← НОВЫЙ: логирование транзакций, история
├── gui/
│   ├── MarketGuiBuilder    ← НОВЫЙ: создание инвентарей
│   ├── MarketClickHandler  ← НОВЫЙ: обработка кликов, торговля
│   └── MarketItemFactory   ← НОВЫЙ: создание предметов для GUI
└── listeners/
    └── MarketGuiListener ← Облегчён: делегирует gui/* классам

vkchat_donate/
├── DonateCommand     ← Рефакторинг: таблица подкоманд
├── DonateManager     ← Рефакторинг: LuckPerms API, save-ahead
└── VKChatDonatePlugin ← Без изменений
```

См. полные рефакторинг-файлы в директории проекта.
