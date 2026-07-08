# ═══════════════════════════════════════════
# VKChatDonate — ПОЛНАЯ ИНСТРУКЦИЯ
# Донаты через DonatePay + LuckPerms + BossBar
# ═══════════════════════════════════════════

## Обзор

Плагин принимает донаты через DonatePay.ru, выдаёт LuckPerms-статусы на 30 дней,
бонусы (скидки, дома, множители рынка/работ), BossBar сбора средств,
и автоматически конвертирует мелкие донаты в репутацию (1₽ = 100 реп).


## 1. ПОДКЛЮЧЕНИЕ DONATEPAY

### Где взять API-токен:

  1. Зайди на https://donatepay.ru/
  2. «Мои кассы» → выбери кассу (или создай новую)
  3. Вкладка «Настройки» → «API»
  4. Скопируй «API-токен»

### Настройка в конфиге:

  api-token: "твой_токен"
  poll-interval: 30        # проверка каждые 30 секунд

### ИЛИ через команду в игре:

  /donate setup <токен>


## 2. КАК ИГРОКУ ЗАДОНАТИТЬ

  1. Перейти по ссылке на страницу доната (из кассы DonatePay)
  2. Ввести сумму
  3. В поле «Ваше имя» (или «Имя отправителя») указать СВОЙ НИКНЕЙМ НА СЕРВЕРЕ
  4. Оплатить — плагин найдёт игрока по нику и выдаст статус/репутацию


## 3. ДОНАТ-СТАТУСЫ

5 уровней статусов с нарастающими бонусами:

| Статус | Цена | Скидка | КД ТП | Дома | Рынок | Jobs XP |
|--------|------|--------|-------|------|-------|---------|
| ⚡ Искра | 250₽ | 10% | ×0.80 | 5 | ×1.10 | ×1.10 |
| 🔥 Пламя | 500₽ | 20% | ×0.65 | 8 | ×1.20 | ×1.20 |
| ⭐ Звезда | 1000₽ | 35% | ×0.50 | 12 | ×1.35 | ×1.35 |
| 👑 Легенда | 2500₽ | 50% | ×0.30 | 20 | ×1.50 | ×1.50 |
| 💎 Властелин | 5000₽ | 65% | ×0.15 | 30 | ×1.70 | ×1.70 |

- **Скидка** — скидка на репутацию (телепорты, магазины, события)
- **КД ТП** — множитель кулдауна телепортаций (×0.15 = в 6.7 раз быстрее)
- **Дома** — максимум точек дома (/sethome)
- **Рынок** — множитель цен продажи на бирже
- **Jobs XP** — множитель опыта профессий

### Как работают статусы:

- **Первый донат:** если сумма >= цены статуса → игрок получает этот статус на 30 дней
- **Продление:** если у игрока уже есть статус и он донатит ту же сумму → статус продлевается ещё на 30 дней
- **Повышение:** если донат больше текущего статуса → старый снимается, выдаётся новый
- **Понижение невозможно:** если у игрока 👑 Легенда, донат на 500₽ не понизит до 🔥 Пламя — только продлит Легенду
- **LuckPerms:** каждый статус = группа LP + право `vkchat.donate.<id>` на 30 дней


## 4. ПОКУПКА РЕПУТАЦИИ

Если донат меньше минимального статуса (250₽):

  rep-purchase:
    enabled: true           # включено
    rate: 100               # 1₽ = 100 репутации
    max-without-status: 0   # 0 = без ограничений

- Игрок задонатил 50₽ → получает 5 000 репутации
- Игрок задонатил 200₽ → получает 20 000 репутации
- Броадкаст: `💎 player пополнил баланс на 5000 реп. за 50₽`

Если НЕ нужна авто-конвертация:
  rep-purchase:
    enabled: false


## 5. BOSSBAR СБОРА СРЕДСТВ

Визуальная полоска прогресса сбора на конкретную цель.

### Включение через конфиг:

  fundraiser:
    enabled: true
    goal: 10000                           # цель в рублях
    bar-color: "PURPLE"                   # PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
    bar-text: "&d💰 Сбор: &f{collected}₽ &7/ &f{goal}₽ &a({percent}%)"

### Плейсхолдеры для bar-text:

  {collected}   — сколько уже собрано (₽)
  {goal}        — цель (₽)
  {percent}     — процент выполнения

### Команды:

  /donate fundraiser start 15000    — запустить сбор на 15 000₽
  /donate fundraiser stop           — остановить сбор
  /donate fundraiser                — показать текущий статус
  /donate fundraiser toggle         — скрыть/показать BossBar (только для донатеров!)

### Как работает:

- BossBar виден всем игрокам онлайн
- При каждом донате прогресс обновляется автоматически
- При рестарте сервера прогресс сохраняется
- Новые игроки видят бар при входе
- Донатеры могут скрыть бар через `/donate fundraiser toggle`
- Обычные игроки не могут скрыть бар

### Цвета BossBar:

  PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE


## 6. НАСТРОЙКА LUCKPERMS

При первом запуске или после `/donate setup` плагин автоматически создаст
5 LP-групп с префиксами, весами и правами:

  /donate setup <токен>

Что делает setup:
  - Создаёт группы: spark, flame, star, legend, overlord
  - Устанавливает вес (1-5) для иерархии
  - Прописывает префиксы в TAB
  - Выдаёт права: vkchat.donate.<group>, vkchat.donate.fundraiser.toggle

После setup проверь:
  /lp editor — все 5 групп должны быть видны
  /lp user <ник> info — после доната должны быть права


## 7. ВСЕ КОМАНДЫ

### Для всех игроков:

  /donate              — информация о статусах и ценах
  /donate info         — то же самое
  /donate status       — твой текущий статус и бонусы
  /donate days         — сколько дней осталось до окончания статуса
  /donate top          — топ-10 донатеров по сумме

### Для админов (vkchat.donate.admin):

  /donate setup <токен>         — настроить токен и LuckPerms
  /donate reload                — перезагрузить конфиг
  /donate fundraiser start <₽>  — запустить сбор средств
  /donate fundraiser stop       — остановить сбор средств
  /donate fundraiser toggle     — скрыть/показать BossBar (только донатеры!)
  /donate fundraiser            — статус сбора


## 8. ПРИМЕР ПОЛНОГО КОНФИГА

  config-version: 1

  api-token: "dpy_xxxxxxxxxxxxxxxx"
  poll-interval: 30

  broadcasts:
    enabled: true

  statuses:
    spark:
      id: "spark"
      name: "&b⚡ Искра"
      display: "⚡ Искра"
      prefix: "&b&lИСКРА&r"
      weight: 1
      price: 250
      description: "Начальный донат-статус"
      rep-discount: 0.10
      tp-cooldown-mult: 0.80
      max-homes: 5
      market-mult: 1.10
      jobs-xp-mult: 1.10

    flame:
      id: "flame"
      name: "&6🔥 Пламя"
      display: "🔥 Пламя"
      prefix: "&6&lПЛАМЯ&r"
      weight: 2
      price: 500
      rep-discount: 0.20
      tp-cooldown-mult: 0.65
      max-homes: 8
      market-mult: 1.20
      jobs-xp-mult: 1.20

    star:
      id: "star"
      name: "&e⭐ Звезда"
      display: "⭐ Звезда"
      prefix: "&e&lЗВЕЗДА&r"
      weight: 3
      price: 1000
      rep-discount: 0.35
      tp-cooldown-mult: 0.50
      max-homes: 12
      market-mult: 1.35
      jobs-xp-mult: 1.35

    legend:
      id: "legend"
      name: "&5👑 Легенда"
      display: "👑 Легенда"
      prefix: "&5&lЛЕГЕНДА&r"
      weight: 4
      price: 2500
      rep-discount: 0.50
      tp-cooldown-mult: 0.30
      max-homes: 20
      market-mult: 1.50
      jobs-xp-mult: 1.50

    overlord:
      id: "overlord"
      name: "&d💎 Властелин"
      display: "💎 Властелин"
      prefix: "&d&lВЛАСТЕЛИН&r"
      weight: 5
      price: 5000
      rep-discount: 0.65
      tp-cooldown-mult: 0.15
      max-homes: 30
      market-mult: 1.70
      jobs-xp-mult: 1.70

  fundraiser:
    enabled: false
    goal: 10000
    bar-color: "PURPLE"
    bar-text: "&d💰 Сбор: &f{collected}₽ &7/ &f{goal}₽ &a({percent}%)"

  rep-purchase:
    enabled: true
    rate: 100
    max-without-status: 0

  messages:
    donate-broadcast-mc: "&6💰 &e{player} &6получил статус {status} &6за донат!"
    donate-broadcast-vk: "💰 {player} получил статус {status} за донат!"
    donate-thanks: "&a✨ Спасибо за донат, {player}! Статус {status} активирован на 30 дней."


## 9. ПРОВЕРКА РАБОТЫ

  1. `/donate setup <токен>` — настроить токен и LP
  2. `/donate` — проверить что статусы отображаются
  3. Сделать тестовый донат 10₽ с ником в имени
  4. Проверить консоль — должен быть лог «РЕП-ДОНАТ: nick → 1000 реп»
  5. Сделать донат на 250₽+ — должен появиться статус в TAB
  6. `/donate fundraiser start 5000` — должен появиться BossBar
  7. `/donate fundraiser toggle` — донатер может скрыть/показать
