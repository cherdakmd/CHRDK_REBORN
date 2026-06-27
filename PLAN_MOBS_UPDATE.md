# План: Доработка модуля vkchat_mobs — фиксы + 5 новых фишек

## Контекст
- 6 Java файлов, ~2816 строк
- 5 архетипов, 5 элементов, 4 tier элиток, контракты, осады, Events 2.0
- Мёртвый код, дублирование, неактуальные drop-таблицы

---

## ЧАСТЬ 1: Исправления

### 1. build.gradle — Удалить PlaceholderAPI
- Убрать `compileOnly 'me.clip:placeholderapi:2.11.5'` (нигде не используется)

### 2. config.yml — Обновление конфига
- Добавить все 22 сета в `hardcore-mobs.rewards.set-fragments` и `set-fragment-names`
- Удалить мёртвые ключи: `events2.effects-level`, `events2.threats.allow-wilderness-block-damage`, `hardcore-mobs.anti-farm.no-spawner-rare-rewards`, `reputation.max-farm-per-hour`
- Добавить конфиг для нового элемента «Свет» и архетипа «Охотник»
- Добавить конфиг для шторма мобов

### 3. VKChatMobsPlugin.java — Общий метод createSetFragment()
- Добавить публичный статический метод `createSetFragment()` для HardcoreMobManager и MobsEvents2Manager

### 4. HardcoreMobManager.java — Убрать дублирование
- Заменить вызов приватного `createSetFragment()` на `VKChatMobsPlugin.createSetFragment()`
- Удалить приватный метод (строка 264)

### 5. MobsEvents2Manager.java — Убрать дублирование
- Заменить вызов приватного `createSetFragment()` на `VKChatMobsPlugin.createSetFragment()`
- Удалить приватный метод (строка 309)

### 6. MobListener.java — Обновить drop-таблицы
- Добавить Ancient Crystal (Heart of the Sea) в `rollRandomGearItem()`
- Обновить диапазоны: Common 0-20, Rare 20-45, Legendary 45-55, Ancient 55-60, Safety Scroll 60-75, Rune 75-100
- Добавить масштабируемый дроп рун: шанс = base + (rank * 2), max 50%

---

## ЧАСТЬ 2: Новые фишки

### 7. Новый элемент: Свет (light)
**Файлы:** HardcoreMobManager.java, config.yml
- Добавить `"light"` в массив ELEMENTS
- Эффект в `applyAbility()`: оглушение (Nausea 2сек) + урон нежити (если цель — Skeleton/Zombie/Wither)
- В `particleFor()`: Particle.ENCHANTMENT_TABLE
- В `elementName()`: "Света"
- Добавить в config.yml шанс появления: `hardcore-mobs.elements.light.chance: 10`

### 8. Новый архетип: Охотник (hunter)
**Файлы:** HardcoreMobManager.java, config.yml
- Добавить `"hunter"` в массив ARCHETYPES
- Постоянные эффекты: Speed I + Night Vision
- Способность: ставит ловушку (Slow + Damage в зоне 3x3)
- В `archetypeName()`: "Охотник"
- В `applyAbility()`: case "hunter" — ловушка: Speed II 3сек + урон 4.0

### 9. Шторм мобов (мировое событие)
**Файлы:** MobStormManager.java (новый), VKChatMobsPlugin.java, config.yml
- Новый класс `MobStormManager`
- Триггер: при убийве мини-босса 10% шанс (configurable)
- Эффект: 50 мобов спавнятся волнами (10/15/25) за 30 секунд
- Мобы: случайные типы (Zombie, Skeleton, Spider, Creeper, Cave Spider)
- Ранг мобов: 5, множитель: 2.0
- Объявление в чат: "⚡ ШТОРМ МОБОВ! Волны монстров обрушиваются на мир!"
- Через 30 секунд шторм заканчивается

### 10. Масштабируемый дроп рун
**Файлы:** MobListener.java, HardcoreMobManager.java
- Формула: `runeChance = 5 + (rank * 3)`, max 50%
- Вместо фиксированного шанса в конфиге, используется динамический расчёт
- Добавить в конфиг: `hardcore-mobs.rewards.rune-token-base-chance: 5`

### 11. Элементальные контракты
**Файлы:** ContractManager.java
- Новый тип контракта: `ELEMENTAL("elemental", "Стихийный Охотник", "Убить 20 мобов определённого элемента", 20, 300, 2, 1)`
- Выбирается случайный элемент из доступных
- Прогресс отслеживается по PDC элемента моба
- Доступен с 5+ выполненных контрактов

---

## Файлы для изменения

| # | Файл | Изменения |
|---|------|-----------|
| 1 | `vkchat_mobs/build.gradle` | Удалить PlaceholderAPI |
| 2 | `vkchat_mobs/src/main/resources/config.yml` | 22 сета, мёртвые ключи, новые конфиги |
| 3 | `vkchat_mobs/src/main/java/.../VKChatMobsPlugin.java` | Общий createSetFragment(), MobStormManager |
| 4 | `vkchat_mobs/src/main/java/.../managers/HardcoreMobManager.java` | Элемент Свет, Архетип Охотник, удалить дубль |
| 5 | `vkchat_mobs/src/main/java/.../managers/MobsEvents2Manager.java` | Удалить дубль createSetFragment |
| 6 | `vkchat_mobs/src/main/java/.../data/ContractManager.java` | Элементальный контракт |
| 7 | `vkchat_mobs/src/main/java/.../listeners/MobListener.java` | Ancient Crystal, масштабируемый дроп рун |
| 8 | `vkchat_mobs/src/main/java/.../managers/MobStormManager.java` | НОВЫЙ ФАЙЛ |

## Порядок работы
1. build.gradle
2. config.yml
3. VKChatMobsPlugin.java (общий метод + MobStormManager)
4. MobStormManager.java (новый файл)
5. HardcoreMobManager.java (свет + охотник + удаление дубля)
6. MobsEvents2Manager.java (удаление дубля)
7. ContractManager.java (элементальный контракт)
8. MobListener.java (Ancient Crystal + масштабируемый дроп)
9. Сборка + проверка
10. Коммит + пуш
