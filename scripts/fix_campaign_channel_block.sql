-- =============================================================================
-- Validate and fix dialer channel blocking (effective_max=0)
-- Database: call_center | Table: calls
-- Set @id_campaign before running (default 1 = Salida-Agendadas)
-- =============================================================================

USE call_center;

SET @id_campaign := 1;

SET @max_canales := (
    SELECT COALESCE(NULLIF(max_canales, 0), 999999)
    FROM campaign
    WHERE id = @id_campaign
);

-- 1) VALIDATION (read-only)
SELECT '=== Dialer active count (same as _countActiveCalls) ===' AS seccion;

SELECT
    @id_campaign AS id_campaign,
    @max_canales AS max_canales,
    COUNT(*) AS active_calls,
    GREATEST(0, @max_canales - COUNT(*)) AS effective_max,
    IF(COUNT(*) >= @max_canales, 'BLOCKED', 'OK') AS estado
FROM calls
WHERE id_campaign = @id_campaign
  AND (
        status IN ('Placing', 'Ringing', 'OnQueue', 'OnHold')
        OR (status = 'Success' AND end_time IS NULL)
      );

-- 2) FIX (uncomment START TRANSACTION through COMMIT after reviewing counts)

/*
START TRANSACTION;

UPDATE calls
SET status = 'Failure', failure_cause = 0,
    failure_cause_txt = 'Orphaned call at startup (SQL fix)'
WHERE id_campaign = @id_campaign
  AND status IN ('Placing', 'Ringing', 'OnQueue');

UPDATE calls
SET status = 'Hangup',
    end_time = COALESCE(start_time, datetime_originate, NOW())
WHERE id_campaign = @id_campaign
  AND status = 'OnHold'
  AND end_time IS NULL;

UPDATE calls
SET status = 'Hangup',
    end_time = COALESCE(datetime_originate, fecha_llamada, NOW())
WHERE id_campaign = @id_campaign
  AND status = 'Success'
  AND end_time IS NULL
  AND start_time IS NULL;

UPDATE calls
SET status = 'Hangup', end_time = start_time
WHERE id_campaign = @id_campaign
  AND status = 'Success'
  AND end_time IS NULL
  AND start_time IS NOT NULL
  AND start_time < DATE_SUB(NOW(), INTERVAL 2 HOUR);

COMMIT;
*/

-- 3) POST-CHECK
SELECT COUNT(*) AS active_calls_after
FROM calls
WHERE id_campaign = @id_campaign
  AND (
        status IN ('Placing', 'Ringing', 'OnQueue', 'OnHold')
        OR (status = 'Success' AND end_time IS NULL)
      );
