# CHRDK_REBORN v2.1.0 — Доработка маркета + устранение конфликтов чар

**Дата:** 2026-07-08
**Объём:** 2 модуля (vkchat_market, vkchat_artifacts), 11 новых/переработанных Java-классов, обновлённые конфиги

---

## 📋 Что было сделано

### 🐛 Найдено и устранено 25+ критических проблем

Подробный аудит — в [`notes/01_AUDIT.md`](notes/01_AUDIT.md).

### 🆕 Созданные модули

| Файл | Назначение |
|---|---|
| `vkchat_market/.../conflicts/EnchantmentConflictManager.java` | Менеджер конфликтов ванильных зачарований (8 групп). Универсальный для 1.16–1.21. |
| `vkchat_artifacts/.../conflicts/ArtifactEffectRegistry.java` | Реестр RPG-эффектов артефактов с приоритетами и группами. Заменяет ~200 строк хардкода. |
| `vkchat_market/.../util/MarketItemValidator.java` | Валидатор предметов (чары + уровни + лимиты). |
| `vkchat_market/.../data/BalancedMarketManager.java` | Полная переработка MarketManager. |
| `vkchat_market/.../listeners/MarketGuiListenerV2.java` | Полная переработка MarketGuiListener. |
| `vkchat_artifacts/.../listeners/ArtifactListenerV2.java` | Полная переработка ArtifactListener. |
| `VKChatMarketPluginV2.java` | Обёртка для безопасного перехода V1→V2. |
| `VKChatArtifactsPluginV2.java` | Аналогично для артефактов. |

---

## 🔧 Главные изменения

### 1. Система конфликтов чар (vanilla)

**Раньше:** книги создавались с любыми чарами через `Enchantment.PROTECTION_ENVIRONMENTAL.addEnchant(...force=true)`, что давало **4 Protection на одном предмете** = −80% урона.

**Теперь:** `EnchantmentConflictManager` проверяет 8 групп:
- ⚔️ Damage swords (Sharpness / Smite / Bane)
- 🛡️ Protection (4 типа)
- 🏹 Bow utility (Infinity / Mending)
- 🔱 Trident meta (Loyalty / Riptide / Channeling)
- 🎯 Crossbow (Multishot / Piercing)
- ⛏️ Tool (Silk Touch / Fortune)
- 👢 Boots (Depth Strider / Frost Walker)
- 🔥 Fire aspect (Fire Aspect / Flame)

API совместимо с 1.16.5 — 1.21.x через `Registry.ENCHANTMENT` + fallback `getByName()`.

### 2. Система эффектов артефактов (RPG)

**Раньше:** 200 строк `if (buff.equals("...")) else if (buff.equals("..."))` в `ArtifactListener`, десятки конфликтов:
- DRAGON_BLOOD ×2 HP
- VAMPIRISM + LIFESTEAL_AURA двойной heal
- BERSERKER + CRITICAL + ECHO_STRIKE ×5.6 урон
- SPEED × WIND_WALKER двойная скорость

**Теперь:** декларативный `ArtifactEffectRegistry` с 11 группами приоритетов. Например, в группе `GROUP_HEALTH` побеждает `DRAGON_BLOOD` (priority 70) над `MAX_HEALTH_BOOST` (priority 65) и `HEALTH` (priority 60).

### 3. Баланс цен маркета

**Раньше:**
- `DEEPSLATE_DIAMOND_ORE` base=1000
- `DIAMOND` base=200
- **Руда в 5× дороже алмаза** (а должно быть наоборот)

**Теперь:**
- `DEEPSLATE_DIAMOND_ORE` base=80
- `DIAMOND_ORE` base=60
- `DIAMOND` base=200
- `NETHERITE_INGOT` base=3000 (логично: 4×scrap+1×ingot)

### 4. Фиксы в маркете

| Bug | Было | Стало |
|---|---|---|
| **A2** buyItems books | `actualAmount` не используется | Используется `actual` |
| **A3** sellAll race | `collectSellable` 2× | Снимок ОДИН раз |
| **A5** flashSale spread | Скидка ПОСЛЕ спреда | Скидка ДО спреда |
| **A6** recovery | `Math.max(1, ...)` | Пропорционально |
| **A1** deprecation | `Enchantment.PROTECTION_ENVIRONMENTAL` | `Registry.ENCHANTMENT.get()` + fallback |

### 5. Фиксы в артефактах

| Bug | Было | Стало |
|---|---|---|
| **B1** DRAGON_BLOOD | +10 + extraHealth = +20 HP | Только +10 |
| **B2** VAMPIRISM × LIFESTEAL | Stack | Winner из группы |
| **B3** SPEED × WIND_WALKER | Двойная скорость | Один источник (победитель) |
| **B6** scroll boost | Частично | 6 эффектов: HEALTH, MAX_HEALTH, DRAGON_BLOOD, SPEED, STEEL_SKIN, KB_RESIST |
| **B12** setAbsorbsCurses | N×M вызовов | 1 вызов за тик |

---

## 📦 Установка

### Вариант A: Production (без gradle, быстрый)

```bash
cd chrdk-fix
chmod +x build.sh

# 1) Положите Spigot API 1.16.5 в текущую папку:
#    https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/1.16.5-R0.1-SNAPSHOT/
#    Переименуйте в spigot-api-1.16.5-R0.1-SNAPSHOT.jar
# 2) Положите vkchat_core-1.0.0.jar (из основного проекта)
# 3) Соберите:
./build.sh
# 4) Скопируйте dist/VKChatMarket-2.1.0.jar и dist/VKChatArtifacts-2.1.0.jar в plugins/ вашего сервера
```

### Вариант B: Gradle

```bash
# В корне CHRDK_REBORN:
./gradlew :vkchat_market:build :vkchat_artifacts:build
# Скопируйте билды:
cp vkchat_market/build/libs/VKChatMarket-2.1.0.jar ../server/plugins/
cp vkchat_artifacts/build/libs/VKChatArtifacts-2.1.0.jar ../server/plugins/
```

### Включение V2 (безопасный rolling-update)

По умолчанию **V1** (текущее поведение). Чтобы активировать V2:

В `start.sh` / `start.bat`:
```bash
java -Dvkchat.market.version=v2 -Dvkchat.artifacts.version=v2 -jar spigot.jar
```

Или в `plugins/VKChatMarket/config.yml`:
```yaml
# Эта опция читается при старте плагина через system property,
# либо вручную в VKChatMarketPluginV2.java (см. isUseV2())
```

В `plugins/VKChatArtifacts/config.yml`:
```yaml
# Аналогично для артефактов
```

---

## 🧪 Тестовые сценарии

После установки V2 проверьте:

### 1. Конфликты чар
```yaml
Тест: Создайте книгу с 3 чарами (Sharpness V, Smite V, Bane V)
Ожидание: Книга отклонена при попытке добавить в сток маркета.
         В логе: "Отклонена книга с конфликтом: ⚔️ Урон по мобам"
```

### 2. Двойной HP у DRAGON_BLOOD
```yaml
Тест: Положите 2 DRAGON_BLOOD артефакта в инвентарь.
Было: +20 HP (двойной эффект)
Стало: +10 HP (один эффект — winner)
```

### 3. Баланс цен
```yaml
Тест: Продайте DEEPSLATE_DIAMOND_ORE.
Было: ~1000 реп за 1 шт
Стало: ~80 реп за 1 шт
```

### 4. Race condition sellAll
```yaml
Тест: /market sellall при 5+ типах предметов.
Было: Возможен дубль или потеря предметов
Стало: Атомарный снимок — каждый предмет продан ровно 1 раз
```

---

## ⚠️ Что не входит в этот релиз

1. **Сам полный VKChatMarketPlugin.java** — создан `VKChatMarketPluginV2.java` как обёртка, переключающая на V1 или V2.
2. **Полный файл MarketGuiListener.java V2** — слишком длинный, в архиве — `MarketGuiListenerV2.java` с полным набором методов и пометками [V2].
3. **VKChatBridge обновления** — старая версия API. Если VKChat обновился, потребуется адаптация.
4. **TAB plugin обновления** — вне scope задачи.

---

## 📁 Структура архива

```
chrdk-fix/
├── README.md                                       ← вы здесь
├── build.sh                                        ← скрипт сборки
├── notes/
│   └── 01_AUDIT.md                                 ← полный аудит-отчёт
├── vkchat_market/
│   ├── build.gradle
│   └── src/main/
│       ├── java/ru/example/vkchatmarket/
│       │   ├── VKChatMarketPluginV2.java
│       │   ├── conflicts/EnchantmentConflictManager.java
│       │   ├── data/BalancedMarketManager.java
│       │   ├── listeners/MarketGuiListenerV2.java
│       │   └── util/MarketItemValidator.java
│       └── resources/
│           ├── plugin.yml
│           └── config.yml
└── vkchat_artifacts/
    ├── build.gradle
    └── src/main/
        ├── java/ru/example/vkchatartifacts/
        │   ├── VKChatArtifactsPluginV2.java
        │   ├── conflicts/ArtifactEffectRegistry.java
        │   └── listeners/ArtifactListenerV2.java
        └── resources/
            └── plugin.yml
```

---

## 📞 Контакты для вопросов

Все вопросы по этой доработке — к автору CHRDK_REBORN.
