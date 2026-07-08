# VKChat Ultimate 2.0.5 — Интеграция VKLongPollManager

**Дата релиза:** 26 июня 2026

## Основные изменения

### ✨ Интеграция

- `VKLongPollManager` интегрирован в `VKChatPlugin`
- При запуске сервера теперь используется **новый улучшенный LongPoll**
- Старый `VKManager.startLongPoll()` больше не вызывается автоматически

### 🔄 Изменения в VKChatPlugin

- Добавлено поле `vkLongPollManager`
- В `onEnable()` теперь вызывается `vkLongPollManager.start()`
- В `onDisable()` вызывается `vkLongPollManager.stop()`
- Добавлен геттер `getVkLongPollManager()`

### 🛡️ Преимущества

- Сервер теперь использует более стабильный и отказоустойчивый LongPoll
- Лучшая обработка сетевых ошибок и reconnect
- Подготовка к постепенному отказу от старого `VKManager` LongPoll кода

## Что было сделано

- Интеграция `VKLongPollManager` в основное ядро
- Обновлены методы запуска/остановки

## Следующий этап

В релизе `2.0.6` планируется:
- Постепенная миграция методов из `VKManager` в `VKLongPollManager`
- Улучшение rate limiting и мониторинга

---

**Коммит:** `feat(vk): integrate VKLongPollManager 2.0.5`
