-- Таблица отслеживаемых рейсов
CREATE TABLE flights (
    id BIGSERIAL PRIMARY KEY,
    flight_iata VARCHAR(10) NOT NULL,
    departure_iata VARCHAR(5) NOT NULL,
    arrival_iata VARCHAR(5) NOT NULL,
    scheduled_departure_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_departure_time TIMESTAMP WITH TIME ZONE,
    scheduled_arrival_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_arrival_time TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    delay_minutes INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_flights_iata ON flights(flight_iata);
CREATE INDEX idx_flights_departure ON flights(departure_iata, scheduled_departure_time);

-- Аудит изменений статусов и задержек
CREATE TABLE flight_status_logs (
    id BIGSERIAL PRIMARY KEY,
    flight_id BIGINT NOT NULL REFERENCES flights(id) ON DELETE CASCADE,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    delay_minutes INT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_flight_logs_flight_id ON flight_status_logs(flight_id);
