# Troubleshooting

## VK-кнопки не работают

1. Замените `VKChat-2.0.0-all.jar` на актуальный.
2. Перезапустите сервер.
3. Проверьте, что бот пишет в ЛС, а не только в беседу.
4. Если VK отвергает клавиатуру, в консоли будет строка `VK keyboard API error`.

## DonatePay не выдаёт награды

1. Проверьте `enabled: true`.
2. Проверьте `api.access-token`.
3. Выполните `/donatepay status`.
4. Выполните `/donatepay check`.
5. Убедитесь, что Minecraft-ник указан в имени донатера.

## DonatePay-статус не отображается в TAB

1. Выполните `/donatepay lpsetup`.
2. Установите подготовленный `groups.yml` для TAB.
3. Выполните `/tab reload`.
4. Проверьте LuckPerms:

```text
/lp user <ник> info
/lp group donate_star info
```

## Конфиг не обновился

Большинство модулей добавляют недостающие ключи автоматически и создают backup. Если ключей нет, остановите сервер, удалите старый config или перенесите секцию вручную из `src/main/resources/config.yml`.

## GitHub Desktop показывает конфликты

Чтобы сохранить текущую локальную версию релиза:

```powershell
git checkout --ours .
git add .
git commit -m "Resolve conflicts for VKChat Ultimate 2.0.0"
git push
```

## В архив попал старый jar

Проверьте:

```bash
./tools/verify_release.sh
```

Скрипт падает, если в `plugins_output` есть запрещённый старый jar удалённого модуля.
