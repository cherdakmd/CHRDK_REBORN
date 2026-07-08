# CHRDK REBORN — Minecraft Server Plugins

15 модулей для Paper 1.16.5. ВК-интеграция, РПГ-система, экономика, донат.

## 📚 Гайды

| Гайд | Для кого |
|------|----------|
| [Ультимативный гайд игрока](docs/player-guide.md) | Все игроки — 15 разделов, все команды |
| [Донат-статусы](docs/donate-player-guide.md) | Игроки — бонусы, цены, как донатить |
| [Настройка донат-плагина](docs/donate-setup-guide.md) | Админы — DonatePay, LuckPerms, BossBar |
| [Настройка стримов](docs/streams-setup-guide.md) | Админы — Twitch, ВК-клавиатура, кросс-ссылки |

## 🧩 Модули

| Модуль | Описание |
|--------|----------|
| **vkchat_core** | Ядро: БД, VK LongPoll API, авторизация (bcrypt), репутация, модерация |
| **vkchat_chat** | Чат: локальный/глобальный/торговый, TAB, join/quit сообщения |
| **vkchat_announcer** | Авто-объявления, викторины |
| **vkchat_artifacts** | Артефакты, эликсиры, мировые боссы |
| **vkchat_donate** | DonatePay интеграция, 5 статусов, BossBar сбора, репа за донат |
| **vkchat_events** | 18 катаклизмов, аирдропы, квесты, баунти, вторжения |
| **vkchat_gear** | РПГ-шмот: кузня, заточка, руны, сеты, синтез, зачарования |
| **vkchat_jobs** | 7 профессий, усталость, специализации, дейлики |
| **vkchat_market** | Биржа: 60+ товаров, динамические цены, тренды, Flash Sale |
| **vkchat_mobs** | Элитные мобы, контракты, осады, кровавая луна, шторм |
| **vkchat_nations** | 6 наций, приваты с прокачкой, защиты |
| **vkchat_offline** | Офлайн-шахты, смены, тайники |
| **vkchat_starter** | Стартовый набор, 15 квестов, гайд-книга |
| **vkchat_streams** | Twitch-анонсер: чат ВК + ЛС + клавиатура, /stream reward |
| **vkchat_teleport** | TPA, home, RTP, back, gateway |

## 🔧 Сборка

```bash
./gradlew build copyJars
```

Плагины в `plugins_output/`.
