# Интеграция DonatePay

Модуль:

```text
vkchat_donatepay
```

Jar:

```text
plugins_output/vkchat_donatepay-2.0.0.jar
```

## Что делает модуль

- опрашивает DonatePay API;
- читает успешные донаты;
- не обрабатывает один и тот же transaction id повторно;
- по умолчанию читает Minecraft-ник из имени донатера;
- выдаёт ВК-репутацию за сумму доната;
- выполняет команды от консоли по порогам суммы;
- пишет лог;
- отправляет объявления в чат Minecraft и основную беседу ВК.

## Настройка

После первого запуска откройте:

```text
plugins/VKChatDonatePay/config.yml
```

Главное:

```yml
enabled: false

api:
  access-token: "PUT_DONATEPAY_TOKEN_HERE"
  poll-interval-seconds: 30
```

1. Вставьте API token DonatePay.
2. Настройте награды.
3. Поставьте:

```yml
enabled: true
```

4. Перезапустите сервер или выполните:

```text
/donatepay reload
```

## Важное ограничение API

DonatePay API не следует опрашивать слишком часто. В конфиге по умолчанию стоит 30 секунд.

```yml
api:
  poll-interval-seconds: 30
```

Не ставьте меньше 20 секунд.

## Как игрок указывает ник

По умолчанию модуль читает Minecraft-ник из поля **имя донатера** DonatePay:

```yml
processing:
  player-source: "name"
  player-regex: "([A-Za-z0-9_]{3,16})"
```

Рекомендуемый текст для DonatePay-страницы:

```text
В поле имени донатера укажите ваш Minecraft-ник.
```

Если нужно искать ник ещё и в комментарии, можно поставить `player-source: "both"`.

## Награда репутацией

```yml
rewards:
  reputation-per-rub: 10
```

Например, донат 100₽ даст 1000 репутации ВК, если ник найден и игрок привязан к ВК.

## Команды наград

Пример:

```yml
rewards:
  actions:
    medium:
      min-amount: 200
      commands:
        - "give {player} diamond 2"
        - "xp add {player} 5 levels"
```

Плейсхолдеры:

```text
{player}
{donator}
{amount}
{amount_int}
{comment}
{id}
```

Если ник не найден, команды с `{player}` не выполняются.

## Админ-команды

```text
/donatepay status
/donatepay player <ник>
/donatepay lpsetup
/donatepay reload
/donatepay check
/donatepay setlast <id>
/donatepay test [amount] [player]
```

### `/donatepay check`

Ручная проверка DonatePay API.

### `/donatepay setlast <id>`

Установить последний обработанный transaction id вручную.

### `/donatepay test [amount] [player]`

Проверить выдачу наград без обращения к DonatePay.

## Данные

```text
plugins/VKChatDonatePay/donatepay_data.yml
```

Хранит:

- `last-id`;
- список обработанных transaction id.

## Лог

```text
plugins/VKChatDonatePay/donatepay.log
```

---

## Расширенные функции

### Отложенные награды оффлайн-игрокам

Если игрок указан в донате, но не находится онлайн, модуль может поставить команды и сообщения в очередь:

```yml
processing:
  queue-offline-commands: true
  queue-offline-messages: true
```

При следующем входе игрока на сервер очередь будет выполнена автоматически.

Посмотреть очередь:

```text
/donatepay pending
```

### История донатов

Модуль хранит краткую историю последних донатов:

```text
/donatepay history
```

Лимит записей:

```yml
stats:
  history-limit: 80
```

### Топ донатеров

Команды:

```text
/donatepay top
/donatepay top month
```

`top` показывает общий топ, `top month` — топ текущего месяца.

### Месячная цель

Можно включить цель месяца:

```yml
goals:
  enabled: true
  monthly-target: 5000
  announce-every-percent: 25
```

Команда статуса цели:

```text
/donatepay goal
```

Модуль будет объявлять прогресс цели, например каждые 25%.

### Полный список команд

```text
/donatepay status
/donatepay player <ник>
/donatepay lpsetup
/donatepay reload
/donatepay check
/donatepay setlast <id>
/donatepay test [amount] [player]
/donatepay top
/donatepay top month
/donatepay goal
/donatepay history
/donatepay pending
```

---

## Чтение ника из имени донатера

По умолчанию модуль теперь берёт Minecraft-ник именно из поля имени донатера DonatePay:

```yml
processing:
  player-source: "name"
```

То есть если донатер указал имя `Steve`, модуль попробует выдать награды игроку `Steve`.

Если нужно искать ник ещё и в комментарии, можно вернуть:

```yml
processing:
  player-source: "both"
```

## 4 статуса донатеров

Добавлены 4 месячных DonatePay-статуса. Статус выдаётся на 30 дней, если сумма конкретного доната достигает цены статуса.

```yml
donor-statuses:
  enabled: true
  levels:
    spark:
      name: "&aИскра"
      price: 100
      reputation-multiplier: 2.00
    flame:
      name: "&bПламя"
      price: 500
      reputation-multiplier: 3.50
    star:
      name: "&dЗвезда"
      price: 1500
      reputation-multiplier: 6.00
    legend:
      name: "&6&lЛегенда"
      price: 5000
      reputation-multiplier: 11.00
```

Статусы по умолчанию:

| Статус | Сумма | Бонус |
|---|---:|---:|
| Искра | 100₽ | x2.00 репутации за донаты |
| Пламя | 500₽ | x3.50 репутации за донаты |
| Звезда | 1500₽ | x6.00 репутации за донаты |
| Легенда | 5000₽ | x11.00 репутации за донаты |

При покупке/продлении статуса выполняются `commands-on-purchase`. Статус выдаётся LuckPerms-командой `addtemp` на 30 дней. Если игрок оффлайн, игровые команды могут быть поставлены в очередь и выданы при входе.

Посмотреть игрока:

```text
/donatepay player <ник>
```

---

## LuckPerms-группы для статусов

Модуль умеет создать и настроить группы LuckPerms для DonatePay-статусов.

Команда:

```text
/donatepay lpsetup
```

Она выполняет команды из конфига:

```yml
luckperms:
  setup-commands:
```

Группы по умолчанию:

```text
default
donate_spark
donate_flame
donate_star
donate_legend
```

Группа `default` получает базовые доступы VKChat-модулей и обычный префикс `[Игрок]`.

Наследование:

```text
donate_flame  -> наследует donate_spark
donate_star   -> наследует donate_flame
donate_legend -> наследует donate_star
```

То есть каждый следующий статус получает бонусы предыдущего.

### Автовыдача группы при повышении статуса

При покупке/продлении статуса модуль выполняет `commands-on-purchase`:

```yml
donor-statuses:
  levels:
    flame:
      commands-on-purchase:
        - "lp user {player} parent remove donate_spark"
        - "lp user {player} parent remove donate_flame"
        - "lp user {player} parent remove donate_star"
        - "lp user {player} parent remove donate_legend"
        - "lp user {player} parent addtemp donate_flame {duration}"
```

Так у игрока остаётся только актуальный DonatePay-статус.

### Бонусы групп

По умолчанию в `lpsetup` добавлены только права нашего комплекса VKChat:

- доступ к базовым модулям VKChat;
- префиксы для TAB/чата;
- веса групп;
- `vkchat.donate.status.*` — определение активной категории;
- `vkchat.donate.jobs.*` — множители Jobs-репутации;
- `vkchat.donate.gear.*` — скидки Кузни/Gear;
- `vkchat.donate.market.*` — бонусы рынка;
- `vkchat.donate.teleport.*` — скидки телепортации;
Права сторонних плагинов в стандартной настройке больше не выдаются.

### Актуальная месячная логика статусов

Статус не является вечным рангом. Он покупается донатом на сумму `price` и действует `duration-days`, по умолчанию 30 дней.

Пример:

```yml
donor-statuses:
  levels:
    star:
      price: 1500
      duration-days: 30
      reputation-multiplier: 6.00
      commands-on-purchase:
        - "lp user {player} parent addtemp donate_star {duration}"
```

Если игрок покупает более высокий статус, старые donate-группы удаляются, а новая группа выдаётся временно на месяц.

### Интеграция в `/menu`

В главное меню сервера добавлен пункт **💳 DonatePay-статусы**. Игроки видят:

- цену каждого статуса;
- что статус действует месяц;
- краткое описание бонусов;
- подсказку, что ник нужно указывать в имени донатера DonatePay.

Пункт также добавлен в экономический раздел меню.

### Защита от понижения статуса

Если у игрока уже активен более высокий DonatePay-статус, а он делает донат на сумму более низкого статуса, высокий статус не удаляется и не заменяется.

Пример:

```text
Игрок имеет Легенду.
Игрок донатит на Искру.
Итог: Легенда остаётся, Искра не выдаётся.
```

Донат при этом всё равно учитывается в истории, статистике, обычных наградах и репутации.

### Продление той же категории

Если игрок уже имеет активный статус и покупает тот же статус снова, модуль продлевает его на количество дней статуса.

Пример:

```text
Игрок имеет Звезду.
Игрок покупает Звезду ещё раз.
Итог: Звезда продлевается ещё на 30 дней.
```

Для LuckPerms используется `addtemp ... accumulate`, чтобы срок суммировался.

---

## Внутриигровое GUI статусов

Игрок может открыть меню статусов командой:

```text
/donatepay
/donate
/донат
```

В GUI видно:

- текущий статус игрока;
- цены Искры, Пламени, Звезды и Легенды;
- срок действия статуса;
- множитель репутации;
- описание бонусов;
- подсказку, что Minecraft-ник нужно указать в имени донатера DonatePay.

Админ-команды по-прежнему требуют `vkchat.donatepay.admin`, но просмотр GUI доступен обычным игрокам.

### Бонусы в модулях VKChat

DonatePay-статусы читаются разными модулями комплекса:

| Модуль | Бонус |
|---|---|
| VKChatJobs | множитель репутации за действия профессий |
| VKChatGear | скидка на операции кузни, руны и заточку |
| VKChatMarket | бонус к продаже и скидка на покупку |
| VKChatTeleport | скидка на телепорты |
| VKChatDonatePay | пассивные эффекты, шанс доп. добычи/лута, бонус опыта и репутации |

---

## Только права и бонусы VKChat-комплекса

Стандартная команда `/donatepay lpsetup` больше не выдаёт права сторонних плагинов вроде `essentials.*`.

Группы получают только права нашего комплекса:

```text
vkchat.donate.status.*
vkchat.donate.jobs.*
vkchat.donate.gear.*
vkchat.donate.market.*
vkchat.donate.teleport.*
```

Эти права читаются самими VKChat-модулями.

### Где работают DonatePay-бонусы

| Модуль | Бонус от статуса |
|---|---|
| Jobs | множитель ВК-репутации за рабочие действия |
| Gear / Кузня | скидка на операции кузни, руны, кристаллы и ремонт |
| Market | бонус к продаже и скидка на покупку |
| Teleport | скидка на RTP/home/TPA/gateway |
| DonatePay | пассивные эффекты, шанс доп. добычи/лута, бонус опыта и репутации |

### Default-группа

`/donatepay lpsetup` также настраивает группу:

```text
default
```

Она получает базовые права комплекса:

```text
vkchat.player
vkchat.teleport.player
vkchat.market.player
vkchat.jobs.player
vkchat.gear.player
vkchat.artifacts.player
vkchat.nations.player
vkchat.events.player
```
