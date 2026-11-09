-- Unknown delay is not zero. Keep the legacy history field nullable too.
ALTER TABLE flight_status_logs ALTER COLUMN delay_minutes DROP NOT NULL;
ALTER TABLE flights ALTER COLUMN delay_minutes DROP DEFAULT;
ALTER TABLE flight_status_logs
    ADD COLUMN departure_delay_minutes INTEGER,
    ADD COLUMN arrival_delay_minutes INTEGER,
    ADD COLUMN estimated_arrival TIMESTAMPTZ;
