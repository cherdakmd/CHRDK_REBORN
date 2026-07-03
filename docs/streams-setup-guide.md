# ═══════════════════════════════════════════
# VKChatStreams — Twitch анонсер
# ПОЛНАЯ ИНСТРУКЦИЯ ПО НАСТРОЙКЕ
# ═══════════════════════════════════════════

## Что делает плагин

Проверяет Twitch каждые 5 минут. Когда стример выходит в эфир:
  - анонс в игре (всем игрокам)
  - анонс в беседу ВК с кнопками [Twitch] [YouTube] [VK]
  - ЛС каждому игроку с привязанным ВК
  - когда стрим закончился — уведомление в беседу

Игроки пишут /stream reward — получают награду (репутация + предметы).
/stream list — показывает активные стримы, uptime, сколько наград выдано.


## Права

  vkchat.streams.player   — /stream reward, /stream list (по умолчанию у всех)
  vkchat.streams.admin    — /stream start, /streams check/reset/reload (OP)


## 1. TWITCH — СПОСОБ А (авто-токен, рекомендуется)

Плагин сам получает и обновляет токен. Нужны client-id и client-secret.

### Где брать:

  1. Зайди на https://dev.twitch.tv/console/apps
  2. Войди в аккаунт стримера
  3. Нажми «+ Register Your Application»
  4. Name: CHRDK Stream Checker
  5. OAuth Redirect URL: http://localhost
  6. Category: Application Integration
  7. Нажми Create
  8. Скопируй Client ID
  9. Нажми «New Secret» → скопируй Client Secret

### Конфиг:

  twitch:
    enabled: true
    client-id: "твой_client_id"
    client-secret: "твой_client_secret"
    oauth-token: ""
    channels:
      - "cherdakmd"
      - "streamer2"


## 2. TWITCH — СПОСОБ Б (ручной токен)

  1. Зайди на https://twitchtokengenerator.com/
  2. Нажми «Generate Token»
  3. Скопируй Access Token

### Конфиг:

  twitch:
    enabled: true
    client-id: "твой_client_id"
    client-secret: ""
    oauth-token: "скопированный_токен"
    channels:
      - "cherdakmd"


## 3. КРОСС-ССЫЛКИ (YouTube + VK)

Для каждого Twitch-канала можно указать ссылки на другие платформы.
Они появятся в анонсе и как кнопки в ВК.

  streamers:
    cherdakmd:
      youtube: "https://youtube.com/@CHERDAKMD"
      vk: "https://vk.com/cherdakgroup"
    streamer2:
      youtube: "https://youtube.com/@streamer2"

Если секция streamers пустая — будут только ссылки на Twitch.


## 4. ПЛЕЙСХОЛДЕРЫ

  {channel}       — имя Twitch-канала
  {title}         — название стрима
  {game}          — во что играет
  {viewers}       — кол-во зрителей
  {url}           — ссылка на Twitch
  {uptime}        — сколько идёт стрим (1ч 23м)
  {claimed}       — сколько игроков получили награду
  {links}         — все ссылки (Twitch + YouTube + VK)
  {youtube_url}   — только YouTube
  {vk_url}        — только VK


## 5. КНОПКИ ВК

По умолчанию включены. В беседу приходит сообщение с кнопками:
  [📺 Twitch] [🔴 YouTube] [🔵 VK]

Отключить:
  announcement:
    keyboard: false


## 6. АНОНС В ИГРЕ

По умолчанию включен. Настраивается:

  announcement:
    game-enabled: true
    game:
      - "&4&l⚡ &c&lСТРИМ &4&l⚡"
      - "&7Стример: &f{channel}"
      - "&7Игра: &f{game}"
      - "&b{url}"
      - "&6/stream reward"

Цветовые коды: &4 (красный), &c (светло-красный), &f (белый), &7 (серый),
&6 (золотой), &e (жёлтый), &b (голубой), &l (жирный)

Отключить: game-enabled: false


## 7. НАГРАДЫ

  rewards:
    reputation: 150
    commands:
      - "give {player} diamond 3"
      - "eco give {player} 500"
      - "xp add {player} 30 levels"

{player} заменяется на ник игрока при выдаче.


## 8. КОМАНДЫ

### Игроки:
  /stream reward   — получить награду (1 раз за стрим)
  /stream list     — список активных стримов

### Админы (OP):
  /stream start <channel> <title...> <url>
    Пример: /stream start cherdak Стрим с друзьями https://twitch.tv/cherdak

  /streams check   — принудительная проверка
  /streams reset   — сбросить список (можно заново анонсить)
  /streams reload  — перезагрузить конфиг


## 9. ПОЛНЫЙ ПРИМЕР КОНФИГА

  config-version: 4
  check-interval-minutes: 5

  twitch:
    enabled: true
    client-id: "gp762nuuoqcoxypju8c569..."
    client-secret: "vgj7s92jk3..."
    oauth-token: ""
    channels:
      - "cherdakmd"

  streamers:
    cherdakmd:
      youtube: "https://youtube.com/@CHERDAKMD"
      vk: "https://vk.com/cherdakgroup"

  announcement:
    cooldown-seconds: 300
    keyboard: true
    game-enabled: true
    game:
      - "&4&l⚡ СТРИМ &4&l⚡"
      - "&f{channel} — {game}"
      - "&7{title}"
      - "&b{url}"
      - "&6/stream reward"
    chat:
      - "🔴⚡ НАЧАЛСЯ СТРИМ ⚡🔴"
      - "🎮 {channel} — {game}"
      - "📺 {title}"
      - "👁 {viewers} зрителей"
      - "🔗 {links}"
      - "💎 /stream reward"
    offline: "⭕ {channel} завершил стрим. Наград: {claimed}, шёл {uptime}."
    player-dm: "🎮 {channel} запустил стрим! {title} 🔗 {url}"

  rewards:
    reputation: 150
    commands:
      - "give {player} diamond 3"


## 10. ПРОВЕРКА

  1. Закинь VKChatStreams.jar в plugins/
  2. Настрой client-id и client-secret в конфиге
  3. /streams reload
  4. Начни стрим на своём Twitch-канале
  5. Через 5 минут (или /streams check) будет анонс
  6. /stream list — проверить что стрим виден
  7. /stream reward — проверить награду

  Тест без реального стрима:
    /stream start test "Тест" https://twitch.tv/test
