-- Добавление столбца даты рейса
ALTER TABLE flights ADD COLUMN flight_date DATE;

-- Создание составного уникального индекса для независимого хранения одинаковых номеров в разные дни
CREATE UNIQUE INDEX idx_flights_iata_date_unique
ON flights (flight_iata, flight_date);