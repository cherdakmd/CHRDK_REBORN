# ═══════════════════════════════════════════
# VKChatStreams — ПОЛНАЯ ИНСТРУКЦИЯ ПО НАСТРОЙКЕ
# ═══════════════════════════════════════════

## Что делает плагин
Автоматически проверяет стримы на Twitch / YouTube / VK / VKVideo каждые 5 минут.
Когда стример выходит в эфир:
  - анонс в игре (всем игрокам)
  - анонс в беседу ВК
  - пост на стену группы ВК (с картинкой)
  - ЛС админам ВК (если указаны admin-vk-ids)
  - звук всем онлайн-игрокам

Игроки пишут /stream reward — получают награду (репутация + предметы).


## Права (permissions)

  vkchat.streams.player   — /stream reward, /stream list (по умолчанию у всех)
  vkchat.streams.admin    — /stream start, /streams check, /streams reset, /streams reload (OP)


## 1. TWITCH

### Способ А — АВТО (рекомендуется)

Плагин сам получает и обновляет токен. Нужны только
client-id и client-secret. Токен живёт ~60 дней, плагин
автоматически обновит до истечения.

### Где брать client-id и client-secret:

  1. Зайди на https://dev.twitch.tv/console/apps
  2. Войди в аккаунт стримера
  3. Нажми «+ Register Your Application»
  4. Name: CHRDK Stream Checker
  5. OAuth Redirect URL: http://localhost
  6. Category: Application Integration
  7. Нажми Create
  8. Скопируй Client ID
  9. Нажми «New Secret» → скопируй Client Secret

### Способ Б — РУЧНОЙ токен

Если хочешь указать готовый токен вручную:

  1. Зайди на https://twitchtokengenerator.com/
  2. Нажми «Generate Token»
  3. Скопируй Access Token
  4. Вставь в oauth-token (имеет приоритет над авто-обновлением)
  5. ВНИМАНИЕ: токен истекает через ~60 дней, придётся обновлять вручную

### Что писать в конфиг:

  streams:
    twitch:
      enabled: true
      client-id: "твой_client_id"
      client-secret: "твой_client_secret"
      oauth-token: ""                        # Способ А: оставь пустым
                                             # Способ Б: вставь токен из twitchtokengenerator
      channels:
        - "имя_канала"                        # например: "cherdakmd"


## 2. YOUTUBE

### Где брать API-ключ:

  1. Зайди на https://console.cloud.google.com/apis/credentials
  2. Создай проект (или выбери существующий)
  3. Нажми «+ Create Credentials» → «API Key»
  4. Скопируй ключ
  5. Перейди в «Enabled APIs» → включи «YouTube Data API v3»

### Как узнать channel ID:

  - Зайди на канал YouTube
  - Скопируй URL: https://www.youtube.com/@CHERDAKMD
  - Либо: https://www.youtube.com/channel/UCxxxxxxxxxxxxxxx
  - Нужен именно UCxxxxxxxxxxx (channel ID)
  - Если в URL @никнейм — открой исходный код страницы (Ctrl+U), найди "externalId":"UC....

### Что писать в конфиг:

  streams:
    youtube:
      enabled: true
      api-key: "твой_api_key"
      channels:
        - "UCxxxxxxxxxxxxxxx"


## 3. VK (группа и видео)

### Способ А — АВТО (рекомендуется)

Плагин сам получает токен через VK Direct Auth.
Нужны ID приложения, защищённый ключ, логин и пароль ВК.

### Где брать client-id и secure-key:

  1. Зайди на https://vk.com/apps?act=manage
  2. Нажми «Создать приложение»
  3. Название: CHRDK Stream Checker
  4. Платформа: выбери Standalone-приложение
  5. Нажми «Подключить приложение»
  6. Перейди в настройки приложения
  7. Скопируй «ID приложения» (client-id)
  8. Скопируй «Защищённый ключ» (secure-key)

### Что писать в конфиг:

  streams:
    vk:
      enabled: true
      token: ""                              # Способ А: оставь пустым
      client-id: "1234567"                   # ID приложения ВК
      secure-key: "AbCdEfGhIjKlMnOp"         # Защищённый ключ
      login: "+79991234567"                  # Логин ВК (телефон или email)
      password: "твой_пароль"                # Пароль ВК
      group-id: "123456789"

### Способ Б — РУЧНОЙ токен

Если Auto не работает (например, включена 2FA):

  1. Зайди на https://vkhost.github.io/
  2. Выбери VK Admin
  3. Включи права: wall, photos, video, groups, messages
  4. Авторизуйся
  5. Скопируй access_token из адресной строки
  6. Вставь в token (имеет приоритет над авто)
  7. Меняй раз в 12-24 часа

### Где брать group-id и группы:

  - ID группы — число, например 123456789
  - Узнать ID: https://api.vk.com/method/groups.getById?group_id=screen_name&v=5.131
  - groups — список screen_name групп где стримят (напр. "cherdakgroup")

  streams:
    vk:
      enabled: true
      token: ""                                # Способ А: оставь пустым
      client-id: "1234567"                     # Способ А: ID приложения ВК
      secure-key: "AbCd..."                    # Способ А: защищённый ключ
      login: "+79991234567"                    # Способ А: логин ВК
      password: "password"                     # Способ А: пароль ВК
      group-id: "123456789"                    # ID группы ВК (цифры!)
      groups:
        - "имя_группы_для_проверки"          # screen_name или ID группы где стримят
      wall-post:
        enabled: true
        photo-file: "banner.jpg"             # файл картинки в папке плагина
        photo-attachment: ""                 # или ручной photo-XXX_YYY
      post-template: |
        🔴⚡ НАЧАЛСЯ СТРИМ ⚡🔴

        🎮 {channel} запустил трансляцию!
        📺 {title}
        🔗 {url}
        💎 /stream reward — награда!

  streams:
    vkvideo:
      enabled: true
      token: ""                                # использует тот же авто-токен из vk секции
      channels:
        - "имя_канала_vkvideo"               # например: "cherdak"


## 4. КАРТИНКА ДЛЯ ПОСТА ВК

### Способ 1 — файл из папки плагина (рекомендуется):

  1. Подготовь картинку banner.jpg (или .png), желательно 1280x720
  2. Положи в папку plugins/VKChatStreams/
  3. Плагин сам загрузит её в альбом группы ВК при первом анонсе
  4. Последующие посты используют кеш — без повторной загрузки

### Способ 2 — готовый photo-attachment (для продвинутых):

  1. Загрузи картинку в альбом группы ВК вручную
  2. Открой фото, посмотри ID в URL: photo-123456789_456239018
  3. Вставь в конфиг: photo-attachment: "photo-123456789_456239018"


## 5. РУЧНОЕ ДОБАВЛЕНИЕ СТРИМЕРОВ

Укажи стримера и ссылки на ВСЕ его платформы.
Плагин автоматически добавит ссылки на остальные платформы
в каждый анонс (кросс-промо).

  streams:
    manual:
      cherdak:
        vk: "https://vk.com/cherdakgroup"
        youtube: "https://youtube.com/@CHERDAKMD"
        twitch: "https://twitch.tv/cherdakmd"

Можно добавить сколько угодно стримеров:

      another_streamer:
        vk: "https://vk.com/another"
        youtube: "https://youtube.com/@another"
        twitch: "https://twitch.tv/another"


## 6. ПЛЕЙСХОЛДЕРЫ В ШАБЛОНАХ

Можно использовать в announcement.game, announcement.vk, post-template, admin-dm:

  {platform}       — Twitch / YouTube / VK / VKVideo
  {platform_emoji} — 🟣🟣 / 🔴 / 🔵
  {channel}        — имя канала/стримера
  {title}          — название стрима
  {game}           — во что играет (сейчас только Twitch)
  {viewers}        — кол-во зрителей (сейчас только Twitch)
  {url}            — ссылка на стрим

  {links}          — все кросс-ссылки одной строкой
  {links_vk}       — то же для ВК-чата (с emoji)
  {links_game}     — то же для игры (с цветами)

  {vk_url}         — ссылка на ВК стримера
  {youtube_url}    — ссылка на YouTube стримера
  {twitch_url}     — ссылка на Twitch стримера


## 7. НАСТРОЙКА ЗВУКА

Доступные звуки (Bukkit Sound enum):
  ENTITY_PLAYER_LEVELUP    — стандартный (по умолчанию)
  BLOCK_NOTE_BLOCK_PLING   — музыкальный динь
  ENTITY_EXPERIENCE_ORB_PICKUP — опыт
  UI_TOAST_CHALLENGE_COMPLETE — достижение
  BLOCK_BELL_USE           — колокол

  announcement:
    sound: "BLOCK_NOTE_BLOCK_PLING"


## 8. НАГРАДЫ И МНОЖИТЕЛИ

  rewards:
    reputation: 150                          # базовая репутация ВК

    multipliers:                             # × платформа
      twitch: 1.0
      youtube: 1.0
      vk: 1.5                               # свои стримы ценнее
      vkvideo: 1.5

    commands:                                # команды от консоли
      - "give {player} diamond 3"
      - "eco give {player} 500"
      - "xp add {player} 30 levels"

  {player} заменяется на ник игрока.


## 9. КОМАНДЫ

### Для игроков:
  /stream reward   — получить награду за просмотр (1 раз за стрим)
  /stream list     — список всех активных стримов

### Для админов (OP):
  /stream start <Twitch|YouTube|VK> <канал> <заголовок...> <url>
     Ручной анонс стрима. Пример:
     /stream start Twitch cherdak Стрим с друзьями https://twitch.tv/cherdak

  /streams check   — принудительная проверка стримов сейчас
  /streams reset   — сбросить список объявленных (можно заново анонсить)
  /streams reload  — перезагрузить конфиг


## 10. ПРИМЕР ПОЛНОГО КОНФИГА
(минимально-рабочий вариант)

  config-version: 2
  check-interval-minutes: 5

  streams:
    admin-vk-ids:
      - 123456789                            # твой VK ID для ЛС-уведомлений

    twitch:
      enabled: true
      client-id: "gp762nuuoqcoxypju8c569..."
      client-secret: "vgj7s92jk3..."
      oauth-token: ""
      channels:
        - "cherdakmd"

    youtube:
      enabled: true
      api-key: "AIzaSy..."
      channels:
        - "UCxxxxxxxxxxxxxxx"

    vk:
      enabled: true
      token: ""                               # Способ А: оставь пустым
      client-id: "1234567"
      secure-key: "AbCd..."
      login: "+79991234567"
      password: "password"
      group-id: "123456789"
      groups:
        - "cherdakgroup"
      wall-post:
        enabled: true
        photo-file: "banner.jpg"
      post-template: |
        🔴⚡ НАЧАЛСЯ СТРИМ ⚡🔴
        🎮 {channel}
        📺 {title}
        🔗 {url}
        💎 /stream reward!

    manual:
      cherdak:
        vk: "https://vk.com/cherdakgroup"
        youtube: "https://youtube.com/@CHERDAKMD"
        twitch: "https://twitch.tv/cherdakmd"

  announcement:
    vk-enabled: true
    cooldown-seconds: 300
    sound: "ENTITY_PLAYER_LEVELUP"
    game:
      - "&4&l⚡ СТРИМ &4&l⚡"
      - "&f{channel} запустил стрим!"
      - "&7{title}"
      - "&b{url}"
      - "&e/stream reward"
    vk:
      - "🔴 {channel} запустил стрим! {url}"
    offline: "⭕ {channel} завершил стрим."

  rewards:
    reputation: 150
    multipliers:
      twitch: 1.0
      vk: 1.5
    commands:
      - "give {player} diamond 3"


## 11. ПРОВЕРКА РАБОТЫ

  1. Поставь плагин в plugins/ и перезагрузи сервер (/streams reload)
  2. Посмотри логи — плагин напишет «Платформы настроены» или предупредит что не так
  3. Начни стрим на тестовом канале
  4. Через 5 минут (или /streams check) должен появиться анонс
  5. Напиши /stream list — должен показать активный стрим
  6. Напиши /stream reward — должна выдать награду

  Для теста без реального стрима:
    /stream start Twitch test "Тестовый стрим" https://twitch.tv/test
    — принудительно запустит анонс
