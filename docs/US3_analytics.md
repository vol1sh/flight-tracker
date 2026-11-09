# US-3 — аналитика рейсов

| Метод | URL | Параметры |
|---|---|---|
| GET | `/api/v1/analytics/flights/{flightIata}` | `from`, `to` |
| GET | `/api/v1/analytics/routes` | `departure`, `arrival`, `from`, `to` |
| GET | `/api/v1/analytics/executions` | `flightIata` **либо** `departure` + `arrival`; `from`, `to`, `page=0`, `size=20` |
| GET | `/api/v1/flights/{flightIata}/history` | `date` — существующая история одного выполнения |

Даты ISO `YYYY-MM-DD`, включительно; максимум 366 дней. `page` 0..1000000, `size` 1..100. Номер рейса нормализуется как в US-1 (`su-100` → `SU100`), аэропорты — три латинские буквы в верхнем регистре. Некорректные параметры → HTTP 400.

Summary имеет поля `scope`, `period`, `sampleSize`, `landedCount`, `cancelledCount`, `knownArrivalDelayCount`, `averageDelayMinutes`, `onTimeRate`, `cancellationRate`, `reliabilityScore`, `dataSufficient`, `message`, `dataSource`, `lastUpdatedAt`.

- `sampleSize` — число всех сохранённых выполнений в фильтре, включая незавершённые. События истории его не увеличивают.
- Итоговые метрики используют только `LANDED` и `CANCELLED`.
- Задержка прилёта: отдельное поле `arrival_delay_minutes`; при отсутствии — целые минуты фактического минус плановое прибытие у `LANDED`, минимум 0. Неизвестное значение остаётся `null`; задержка вылета не используется.
- Средняя задержка — сумма известных задержек приземлений / число известных задержек (включая нули).
- Пунктуальность — приземления с известной задержкой 0 / число известных задержек.
- Доля отмен — отмены / (приземления + отмены). Доли возвращаются в диапазоне 0..1.
- Достаточность: **LANDED + CANCELLED >= 10**, есть хотя бы одно приземление и известны задержки минимум 80% приземлений. Применено правило из задания Ильи; это осознанное отличие от `landed >= 10` в issue #90.
- При недостатке данных → HTTP 200, `dataSufficient=false`, метрики и score `null`, счётчики/период/пояснение сохраняются.
- `score = clamp(100 − 0.5 × averageDelay − 200 × cancellationRate, 0, 100)`, вычисляется до округления, выводится с двумя знаками; это проектная эвристика.
- `lastUpdatedAt` — максимальное `last_synced_at` выбранных выполнений. Пустая выборка → `null`. Старая история не объявляется ошибочной из-за возраста timestamp.
- Ошибка БД остаётся серверной ошибкой, а не пустой статистикой. AviationStack и Redis не вызываются.

`executions` возвращает Spring `Page`: `content`, `totalElements`, `totalPages`, `number`, `size`, ... Сортировка стабильная: дата убывает, затем id убывает. Поля выполнения: `flightIata`, `flightDate`, `departureIata`, `arrivalIata`, `status`, `arrivalDelayMinutes`, `scheduledArrivalTime`, `actualArrivalTime`, `lastSyncedAt`.

```bash
curl 'http://localhost:8080/api/v1/analytics/flights/SU100?from=2026-10-01&to=2026-10-31'
curl 'http://localhost:8080/api/v1/analytics/routes?departure=SVO&arrival=LED&from=2026-10-01&to=2026-10-31'
curl 'http://localhost:8080/api/v1/analytics/executions?flightIata=SU100&from=2026-10-01&to=2026-10-31&page=0&size=20'
```

Маленькая либо неравномерно собранная выборка не описывает все реальные рейсы маршрута. Семёну нужно показывать размер выборки, источник и время обновления. `scripts/demo-us3.sql` — **вымышленные** 20 выполнений SU100 в изолированной демонстрационной БД; скрипт отказывается заменять существующую историю. Эталон: 6.67 / 0.7778 / 0.1000 / 76.67.

В `backend` команда `mvn -B clean verify` проверяет сервис, HTTP-контракт и SQL на PostgreSQL 16 через Testcontainers. Для отдельной одноразовой базы без Docker можно передать `-Dtest.postgres.url=jdbc:postgresql://localhost:5432/flight_test -Dtest.postgres.username=test_user -Dtest.postgres.password=test_pass`.
