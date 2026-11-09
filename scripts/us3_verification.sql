SELECT
    COUNT(*) as sampleSize,
    SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) as landedCount,
    SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelledCount,
    SUM(CASE WHEN status = 'LANDED' AND (arrival_delay_minutes IS NOT NULL OR (actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL)) THEN 1 ELSE 0 END) as knownArrivalDelayCount,
    ROUND(CAST(AVG(CASE WHEN status = 'LANDED' THEN COALESCE(arrival_delay_minutes, GREATEST(0, EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60)) END) AS NUMERIC), 2) as averageDelayMinutes,
    ROUND(CAST((SUM(CASE WHEN status = 'LANDED' AND COALESCE(arrival_delay_minutes, GREATEST(0, EXTRACT(EPOCH FROM (actual_arrival_time - scheduled_arrival_time)) / 60)) = 0 THEN 1 ELSE 0 END)::numeric /
     NULLIF(SUM(CASE WHEN status = 'LANDED' AND (arrival_delay_minutes IS NOT NULL OR (actual_arrival_time IS NOT NULL AND scheduled_arrival_time IS NOT NULL)) THEN 1 ELSE 0 END), 0)) AS NUMERIC), 4) as onTimeRate,
    ROUND(CAST((SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END)::numeric /
     NULLIF(SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) + SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0)) AS NUMERIC), 2) as cancellationRate,
    MAX(last_synced_at) as lastUpdatedAt
FROM flights
WHERE flight_iata = 'SU100'
  AND flight_date BETWEEN '2026-10-01' AND '2026-10-31'
  AND status IN ('LANDED', 'CANCELLED');