# План: 5 Мифических сетов + 4-й tier кристаллов + материалы для слияния

## Контекст
- Сервер Purpur 1.20.4, Java 17
- GEAR модуль: 11 Java файлов, ~3500 строк
- 17 текущих сетов (5 славянских + 5 советских + 7 легендарных)
- 3 tier кристаллов: common (0-10), rare (10-15), legendary (15-20)
- Максимальная заточка: +20
- Цель: +25 с новым 4-м tier'ом

## Новые сеты (категория "Мифические")

| Ключ | Название | Баффы | Дебафф | Спецэффект (CombatListener) |
|------|----------|-------|--------|---------------------------|
| `bone_armor` | Костяной Доспех | Сопротивление II + Здоровье II | Медлительность I | Щит при ХП < 20% (Resistance V 5сек, кулдаун 90сек) |
| `shadow_blade` | Клинок Тени | Сила II + Скорость II | Слабость I | 15% вампиризм при ударе |
| `ember_crown` | Пепельная Корона | Огнестойкость + Сила II | Утомление копания | Поджигает атакующего (огонь 3сек) |
| `plague_mist` | Моровой Туман | Яд AoE + Регенерация I | Медлительность II | AoE Poison II 5 блоков |
| `starforged` | Звёздная Ковка | Удача III + Прыгучесть III | Невидимость (ночью) | Регенерация в темноте |

## Новый 4-й tier кристаллов

| Tier | Название | Материал | Диапазон | Шанс | Дебафф при провале |
|------|----------|----------|----------|------|---------------------|
| common | Обычный | EMERALD | 0-10 | 90% | 25% шанс -1 |
| rare | Редкий | DIAMOND | 10-15 | 60% | -1 |
| legendary | Легендарный | PRISMARINE_SHARD | 15-20 | 35% | -1 или -2 |
| **ancient** | **Древний** | **HEART_OF_THE_SEA** | **20-25** | **25%** | **-1 или -3** |

Новый максимум заточки: **+25** (вместо +20)

## Файлы для изменения

### 1. `config.yml` (vkchat_gear/src/main/resources/)
- Добавить 5 новых сетов в секцию `sets:`
- Добавить `ancient` tier в `hardcore-forging.crystals.tiers`
- Обновить `max-upgrade-level` на 25
- Добавить `forge2.resources.ancient` для слияния
- Добавить `destroy-chance.crystal-ancient`

### 2. `GearManager.java` (~951 строк)
- Добавить 5 новых `else if` блоков в `checkSetBonus()` (строка ~850)
- Использовать scaling переменные (strAmp, resAmp, speedAmp, etc.)

### 3. `CombatListener.java` (~655 строк)
- Добавить эффекты для:
  - `bone_armor`: Щит при ХП < 20% (Resistance V 5сек, кулдаун 90сек)
  - `shadow_blade`: 15% вампиризм при ударе
  - `ember_crown`: Поджигает атакующего
  - `plague_mist`: AoE Poison II 5 блоков
  - `starforged`: Регенерация в темноте (свет <= 7)
- Добавить анимацию смерти: при смертельном ударе по носителю `bone_armor` — щит

### 4. `MechanicsListener.java` (~183 строки)
- Добавить particle trails для 5 новых сетов в `onMove()`

### 5. `RuneListener.java` (~434 строки)
- Добавить `ancient` tier в логику кристаллов (строка ~239)
- Обновить `tierFrom`/`tierTo` вычисления

### 6. `RuneCommand.java` (~266 строк)
- Добавить 4-й кристалл в магазин (слот 40)
- Добавить метод `addCrystal` для ancient tier

### 7. `RuneMarketManager.java` (~127 строк)
- Добавить `crystal_ancient` в basePrices

### 8. `ForgeCommand.java` (~825 строк)
- Обновить `materialCostFor()` — ancient tier использует NETHERITE_INGOT
- Обновить `materialAmountFor()` — ancient tier

## Порядок работы

1. `config.yml` — все конфиги
2. `GearManager.java` — set bonuses
3. `CombatListener.java` — combat effects
4. `MechanicsListener.java` — particles
5. `RuneListener.java` — crystal tier logic
6. `RuneCommand.java` — shop
7. `RuneMarketManager.java` — pricing
8. `ForgeCommand.java` — fusion materials
9. Сборка и проверка компиляции
10. Коммит + пуш

## Проверка
- `.\gradlew.bat :vkchat_gear:compileJava` — компиляция
- `.\gradlew.bat clean build` — полная сборка всех 13 плагинов
