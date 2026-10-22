CREATE INDEX IF NOT EXISTS idx_flights_iata_time
ON flights (flight_iata, scheduled_departure_time);