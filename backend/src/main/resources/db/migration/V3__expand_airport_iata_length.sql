-- Расширение длины кодов аэропортов и бортов до 10 символов для поддержки ICAO24 и расширенных кодов
ALTER TABLE flights ALTER COLUMN departure_iata TYPE VARCHAR(10);
ALTER TABLE flights ALTER COLUMN arrival_iata TYPE VARCHAR(10);
