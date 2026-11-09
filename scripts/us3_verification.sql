-- Independent reference over executions; unknown observations stay NULL.
WITH observations AS (
    SELECT status, last_synced_at,
        CASE WHEN status = 'LANDED' THEN
            CASE WHEN arrival_delay_minutes IS NOT NULL THEN GREATEST(0, arrival_delay_minutes)
                 WHEN actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL
                 THEN GREATEST(0, FLOOR(EXTRACT(EPOCH FROM actual_arrival_time - scheduled_arrival_time) / 60))
            END
        END AS delay
    FROM flights
    WHERE flight_iata = 'SU100' AND flight_date BETWEEN '2026-10-01' AND '2026-10-31'
)
SELECT COUNT(*) AS sample_size,
    COUNT(*) FILTER (WHERE status = 'LANDED') AS landed_count,
    COUNT(*) FILTER (WHERE status = 'CANCELLED') AS cancelled_count,
    COUNT(delay) AS known_arrival_delay_count,
    ROUND(AVG(delay), 2) AS average_delay_minutes,
    ROUND(COUNT(*) FILTER (WHERE delay = 0)::numeric / NULLIF(COUNT(delay), 0), 4) AS on_time_rate,
    ROUND(COUNT(*) FILTER (WHERE status = 'CANCELLED')::numeric
          / NULLIF(COUNT(*) FILTER (WHERE status IN ('LANDED', 'CANCELLED')), 0), 4) AS cancellation_rate,
    MAX(last_synced_at) AS last_updated_at
FROM observations;
