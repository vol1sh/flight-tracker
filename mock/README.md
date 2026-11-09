# Тестовый AviationStack (WireMock) — EXT-04

Сценарии лежат в `mock/aviationstack/mappings/*.json` (по несколько в файле). Для QA и демонстрации: backend получает ответы, похожие на AviationStack, **без API-ключа, VPN и расхода квоты**.
Обычный `docker compose up` по-прежнему ходит в настоящий AviationStack.

## Запуск

```bash
docker compose -f docker-compose.yml -f docker-compose.mock.yml up -d --build
```

- Backend: `AVIATION_API_BASE_URL=http://wiremock:8080/v1`, ключ `mock-key` (настоящий ключ из `.env` в мок не отправляется).
- Фоновый опрос включён: каждые 30 с, до 50 рейсов за цикл (`MOCK_POLLER_ENABLED`, `MOCK_POLLER_DELAY_MS`, `MOCK_POLLER_MAX_FLIGHTS`).
- Сам мок: http://localhost:8089/__admin/mappings — список сценариев.

Дата ответа = запрошенная дата (`?date=`), а для запроса без даты — сегодняшняя (UTC).

## Сценарии

| Номер | Ответ поставщика | Что ожидать от сервиса |
|---|---|---|
| `ZZ1001` | SCHEDULED, задержек нет | 200, статус SCHEDULED |
| `ZZ1002` | В полёте: вылет **+30**, прилёт **+10**, прогноз прибытия | 200, DELAYED. Клиент отдаёт `departureDelayMinutes=30`, `arrivalDelayMinutes=10` (в ответе API — после того как поля добавят в `FlightResponseDto`) |
| `ZZ1003` | LANDED, прилёт +5 | 200, LANDED; повторный поиск — из PostgreSQL |
| `ZZ1004` | CANCELLED | 200, CANCELLED |
| `ZZ1005` | В полёте, `arrival.delay` отсутствует | Клиент: `arrivalDelayMinutes = null` (не 0) |
| `ZZ1006` | Медленный ответ, 3 с | 200 (в пределах таймаута 10 с) |
| `ZZ1408` | Зависание 15 с | Таймаут → недоступен → сохранённый рейс (degraded) или 503 |
| `ZZ1500` | HTTP 500 | Недоступен → degraded или 503 |
| `ZZ1429` | HTTP 429 `rate_limit_reached` | Лимит → degraded или 503 |
| `ZZ1403` | Фильтр даты запрещён тарифом (`function_access_restricted`), без даты — рейс есть | Клиент повторяет без даты и сам ищет нужную дату: для сегодняшней даты (UTC) — 200, LANDED |
| `ZZ1999` | `data: []` | 404 «рейс не найден» |
| `ZZ2000` | Меняется при каждом реальном запросе: SCHEDULED → DELAYED → LANDED | Для демонстрации уведомлений US-2 (новое состояние — после истечения кэша, 3 мин) |
| любой другой | `data: []` | 404 |

Пример (дату подставьте сегодняшнюю): `curl -i "http://localhost:8080/api/v1/flights/ZZ1002?date=2026-11-09"`

## Проверка отказа поставщика (для Насти)

```bash
# 1. Найти рейс, пока поставщик работает (данные сохранятся в PostgreSQL)
curl -i "http://localhost:8080/api/v1/flights/ZZ1001?date=2026-11-10"
# 2. «Отключить» AviationStack
docker stop flight_tracker_wiremock
# 3. Подождать 3 мин (кэш) и повторить — ответ из PostgreSQL с "isDegraded": true
curl -i "http://localhost:8080/api/v1/flights/ZZ1001?date=2026-11-10"
# 4. Вернуть
docker start flight_tracker_wiremock
# Сбросить сценарий ZZ2000 в начало
curl -X POST http://localhost:8089/__admin/scenarios/reset
```

## Бюджет запросов к настоящему AviationStack (EXT-03)

- Квота бесплатного тарифа — несколько десятков–сотня запросов **в месяц** (точное число — в Dashboard AviationStack).
- Опрос одного рейса каждые 15 минут = 96 запросов в сутки, то есть квота заканчивается примерно за день.
- Поэтому с настоящим API: опрос выключен по умолчанию (`SCHEDULER_POLLER_ENABLED=false`); если включать — редко, не больше 5 рейсов за цикл (`SCHEDULER_POLLER_MAX_FLIGHTS`), только даты «вчера/сегодня/завтра».
- Один запрос на уникальный рейс (номер + дата), а не на подписчика; ответы кэшируются в Redis на 3 минуты.
- Опрос и демонстрация уведомлений — только с этим моком. Нагрузочное тестирование — тоже только с моком.