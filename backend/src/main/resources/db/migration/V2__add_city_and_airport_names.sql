-- Добавление полей для хранения названий городов и аэропортов
ALTER TABLE flights ADD COLUMN IF NOT EXISTS departure_city VARCHAR(100);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS departure_airport_name VARCHAR(150);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS arrival_city VARCHAR(100);
ALTER TABLE flights ADD COLUMN IF NOT EXISTS arrival_airport_name VARCHAR(150);
