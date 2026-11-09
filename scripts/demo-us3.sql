-- SYNTHETIC QA DATA. Run only against an isolated demo database, never a production/history database.
-- Refuse to replace existing history. All timestamps below belong to fictional observations.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flights WHERE flight_iata = 'SU100'
               AND flight_date BETWEEN '2026-10-01' AND '2026-10-31') THEN
        RAISE EXCEPTION 'Demo range already contains flights; use an empty isolated demo database';
    END IF;
END $$;

INSERT INTO flights (flight_iata, departure_iata, departure_city, departure_airport_name, arrival_iata, arrival_city, arrival_airport_name, scheduled_departure_time, actual_departure_time, scheduled_arrival_time, actual_arrival_time, status, delay_minutes, arrival_delay_minutes, flight_date, last_synced_at)
SELECT
    'SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo',
    ('2026-10-' || lpad(i::text, 2, '0') || ' 08:00:00Z')::timestamptz,
    ('2026-10-' || lpad(i::text, 2, '0') || ' 08:00:00Z')::timestamptz,
    ('2026-10-' || lpad(i::text, 2, '0') || ' 10:00:00Z')::timestamptz,
    ('2026-10-' || lpad(i::text, 2, '0') || ' 10:00:00Z')::timestamptz,
    'LANDED', 0, 0,
    ('2026-10-' || lpad(i::text, 2, '0'))::date,
    '2026-11-01 12:00:00Z'
FROM generate_series(1, 14) AS i;

INSERT INTO flights (flight_iata, departure_iata, departure_city, departure_airport_name, arrival_iata, arrival_city, arrival_airport_name, scheduled_departure_time, actual_departure_time, scheduled_arrival_time, actual_arrival_time, status, delay_minutes, arrival_delay_minutes, flight_date, last_synced_at)
VALUES
    ('SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo', '2026-10-15 08:00:00Z', '2026-10-15 08:15:00Z', '2026-10-15 10:00:00Z', '2026-10-15 10:15:00Z', 'LANDED', 15, 15, '2026-10-15', '2026-11-01 12:00:00Z'),
    ('SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo', '2026-10-16 08:00:00Z', '2026-10-16 08:30:00Z', '2026-10-16 10:00:00Z', '2026-10-16 10:30:00Z', 'LANDED', 30, 30, '2026-10-16', '2026-11-01 12:00:00Z'),
    ('SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo', '2026-10-17 08:00:00Z', '2026-10-17 08:45:00Z', '2026-10-17 10:00:00Z', '2026-10-17 10:45:00Z', 'LANDED', 45, 45, '2026-10-17', '2026-11-01 12:00:00Z'),
    ('SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo', '2026-10-18 08:00:00Z', '2026-10-18 08:30:00Z', '2026-10-18 10:00:00Z', '2026-10-18 10:30:00Z', 'LANDED', 30, 30, '2026-10-18', '2026-11-01 12:00:00Z');

INSERT INTO flights (flight_iata, departure_iata, departure_city, departure_airport_name, arrival_iata, arrival_city, arrival_airport_name, scheduled_departure_time, actual_departure_time, scheduled_arrival_time, actual_arrival_time, status, delay_minutes, arrival_delay_minutes, flight_date, last_synced_at)
VALUES
    ('SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo', '2026-10-19 08:00:00Z', NULL, '2026-10-19 10:00:00Z', NULL, 'CANCELLED', 0, NULL, '2026-10-19', '2026-11-01 12:00:00Z'),
    ('SU100', 'SVO', 'Moscow', 'Sheremetyevo', 'LED', 'Saint Petersburg', 'Pulkovo', '2026-10-20 08:00:00Z', NULL, '2026-10-20 10:00:00Z', NULL, 'CANCELLED', 0, NULL, '2026-10-20', '2026-11-01 12:00:00Z');

INSERT INTO flight_status_logs (flight_id, previous_status, new_status, delay_minutes, recorded_at)
SELECT id, 'SCHEDULED', 'ACTIVE', 0, NOW() FROM flights WHERE flight_iata = 'SU100' AND flight_date = '2026-10-01';

INSERT INTO flight_status_logs (flight_id, previous_status, new_status, delay_minutes, recorded_at)
SELECT id, 'ACTIVE', 'LANDED', 15, NOW() FROM flights WHERE flight_iata = 'SU100' AND flight_date = '2026-10-15';
COMMIT;
