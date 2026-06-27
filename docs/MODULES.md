# Модули VKChat Ultimate

## Список jar-модулей (13)

| # | Модуль | Файл | Назначение |
|---|---|---|---|
| 1 | **VKChat Core** | `VKChat.jar` | Ядро: авторизация, VK-интеграция, команды, репутация |
| 2 | **VKChat Nations** | `VKChatNations.jar` | Нации, приваты, ClaimDefenseManager, фестивали |
| 3 | **VKChat Gear** | `VKChatGear.jar` | Кузня 2.0, 22 набора, 5 Mythical, 4 тира кристаллов, +25, зачарования |
| 4 | **VKChat Mobs** | `VKChatMobs.jar` | Хардкор- mob-система, 6 стихий, 6 архетипов, ELEMENTAL контракт |
| 5 | **VKChat Events** | `VKChatEvents.jar` | 16 катаклизмов, автоспавн, ивенты, защита приватов |
| 6 | **VKChat Jobs** | `VKChatJobs.jar` | 7 профессий, навыки, ежедневки, еженедельки, рейтинг |
| 7 | **VKChat Artifacts** | `VKChatArtifacts.jar` | Артефакты с проклятиями, Mythic шанс, Алхимический тайник |
| 8 | **VKChat Offline** | `VKChatOffline.jar` | DnD-походы через ЛС ВК, 6 маршрутов, классы, компаньоны |
| 9 | **VKChat Market** | `VKChatMarket.jar` | Рынок с трендами, историей, лимитированными товарами |
| 10 | **VKChat DonatePay** | `VKChatDonatePay.jar` | 4 статуса, VIP-команды, интеграция |
| 11 | **VKChat Teleport** | `VKChatTeleport.jar` | Телепорт-система (rtp, home, tpa) |
| 12 | **VKChat Announcer** | `VKChatAnnouncer.jar` | Объявления и уведомления |
| 13 | **VKChat Starter** | `VKChatStarter.jar` | Базовый старт, регистрация |

## Зависимости

```
VKChat Core ← все остальные модули
VKChat Core ← VKChatAPI (библиотека)
```

## Конфиги

Каждый модуль имеет свой `config.yml` в `src/main/resources/`:

| Модуль | Файл конфига |
|---|---|
| Core | `config.yml` |
| Nations | `config.yml` + `nations_data.yml` |
| Gear | `config.yml` |
| Mobs | `config.yml` |
| Events | `config.yml` |
| Jobs | `config.yml` + `jobs_data.yml` + `weekly_tasks.yml` + `ranking.yml` |
| Artifacts | `config.yml` |
| Offline | `config.yml` + `offline_data.yml` |
| Market | `config.yml` |
| DonatePay | `config.yml` |

---

*Версия: v2.1.0 — 35 обновлений*
