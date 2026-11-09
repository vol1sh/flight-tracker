ALTER TABLE flights
    ADD COLUMN IF NOT EXISTS departure_delay_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS arrival_delay_minutes INTEGER;

CREATE INDEX IF NOT EXISTS idx_flights_route_date
    ON flights (departure_iata, arrival_iata, flight_date);

CREATE INDEX IF NOT EXISTS idx_flights_iata_date_status
    ON flights (flight_iata, flight_date, status);