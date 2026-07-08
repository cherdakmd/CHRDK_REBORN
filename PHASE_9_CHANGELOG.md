# CHRDK_REBORN — Global Cross-Module Improvements v3 (Phase 9)

## Дата: 2026-07-08
## Версия: v3.3.0 (продолжение)

---

## Выполненные улучшения (Phase 9)

### #6 MaterialResolver ✅ (Jobs Module)
**Проблема**: JobsListener — 44+ хардкоженных Material (isOre, isCrop, getIngotFromOre, getSeedFromCrop, _LOG, суффиксы кузнеца).

**Решение**: Создан `MaterialResolver` (258 строк) в пакете `resolver`:

| Компонент | Было | Стало |
|-----------|------|-------|
| `isOre()` | 8 хардкод + 4 pattern | config.yml `jobs.materials.ores` |
| `isCrop()` | 4 хардкод + 3 pattern | config.yml `jobs.materials.crops` |
| `getIngotFromOre()` | 4 if-contains | config.yml `jobs.materials.ore-to-ingot` |
| `getSeedFromCrop()` | switch 4 case | config.yml `jobs.materials.crop-to-seed` |
| `_LOG` check | хардкод суффикса | config.yml `jobs.materials.log-suffixes` |
| Кузнец суффиксы | 7 `endsWith()` | config.yml `jobs.materials.blacksmith-suffixes` |

**Формат конфига**: Поддержка точных имён (`DIAMOND_ORE`) и паттернов (`pattern:DEEPSLATE`).

### #7 TeleportCommand Decomposition ✅ (Teleport Module)
**Проблема**: TeleportCommand — 721 строк, 5 донат-утилит дублируются, хардкод координат 3 фракций, хардкод опасных материалов.

**Решение**: Создан пакет `util/`:

| Файл | Строк | Назначение |
|------|-------|------------|
| `util/DonateTierHelper.java` | 98 | Конфиг-управляемые скидки/кулдауны/лимиты по 5 донат-тирам |
| `util/GatewayRegistry.java` | 143 | Конфиг-управляемые порталы фракций (было 3 хардкод) |
| `commands/TeleportCommand.java` | 667 | Упрощён (было 721) |

**Результат**:
- 5 донат-утилит → DonateTierHelper с конфиг-поддержкой (`teleportation.donate-tiers.*`)
- Gateway: 3 хардкод координат → config.yml `teleportation.gateways.*` с алиасами
- Tab-complete автоматически из реестра

---

## Статус всех 12 улучшений

| # | Улучшение | Статус | Фаза |
|---|-----------|--------|------|
| 1 | JobsBridge | ✅ | Phase 7 |
| 2 | CataclysmRegistry | ✅ | Phase 8 |
| 3 | NationGuiListener decomposition | ⏳ | — |
| 4 | Config versioning | ✅ | Phase 7 |
| 5 | SetCombatEffectRegistry | ✅ | Phase 8 |
| 6 | Jobs materials from config | ✅ | Phase 9 |
| 7 | TeleportCommand decomposition | ✅ | Phase 9 |
| 8 | Remove duplicate directory | ✅ | Phase 7 |
| 9 | Bukkit Events API | ✅ | Phase 7 |
| 10 | MarketTransactionService | ⏳ | — |
| 11 | DonateStatusResolver → core | ✅ | Phase 7 |
| 12 | NationListener decomposition | ⏳ | — |

**Выполнено**: 9/12
**Осталось**: 3 (#3, #10, #12)

---

## Рекомендуемый порядок оставшихся

1. **#10 MarketTransactionService** — MarketGuiListener 1006 строк
2. **#3 NationGuiListener** — 1476 строк (самый крупный)
3. **#12 NationListener** — 1176 строк

---

## Архив
`CHRDK_REBORN_global_improvements_v3.tar.gz` (13MB)
