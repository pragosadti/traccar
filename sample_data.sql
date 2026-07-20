-- =====================================================
-- 0. LIMPAR DADOS ANTERIORES
-- =====================================================

DELETE FROM tc_positions WHERE deviceid IN (SELECT id FROM tc_devices WHERE uniqueid LIKE 'SIMU5%');
DELETE FROM tc_user_device WHERE userid = @userId AND deviceid IN (SELECT id FROM tc_devices WHERE uniqueid LIKE 'SIMU5%');
DELETE FROM tc_device_geofence WHERE deviceid IN (SELECT id FROM tc_devices WHERE uniqueid LIKE 'SIMU5%');
DELETE FROM tc_user_geofence WHERE userid = @userId AND geofenceid IN (SELECT id FROM tc_geofences WHERE name IN ('Depot','Factory','Customer A','Customer B'));
DELETE FROM tc_geofences WHERE name IN ('Depot','Factory','Customer A','Customer B');
DELETE FROM tc_devices WHERE uniqueid LIKE 'SIMU5%';

SET @userId = 5;

-- =====================================================
-- 1. CRIAR DEVICES
-- =====================================================

INSERT INTO tc_devices (name, uniqueid)
VALUES
    ('Sim Truck 1','SIMU5001'),
    ('Sim Truck 2','SIMU5002');

-- associar devices ao utilizador
INSERT INTO tc_user_device (userid, deviceid)
SELECT @userId, id
FROM tc_devices
WHERE uniqueid LIKE 'SIMU5%';

-- associar devices às geofences
INSERT INTO tc_device_geofence (deviceid, geofenceid)
SELECT d.id, g.id
FROM tc_devices d, tc_geofences g
WHERE d.uniqueid LIKE 'SIMU5%' AND g.name IN ('Depot','Factory');


-- =====================================================
-- 2. CRIAR GEOFENCES
-- =====================================================

INSERT INTO tc_geofences (name, area, attributes)
VALUES
    (
        'Depot',
        'POLYGON ((-9.1450 38.7250,-9.1450 38.7300,-9.1400 38.7300,-9.1400 38.7250,-9.1450 38.7250))',
        '{"color":"#4caf50"}'
    ),
    (
        'Factory',
        'POLYGON ((-9.1750 38.7400,-9.1750 38.7450,-9.1700 38.7450,-9.1700 38.7400,-9.1750 38.7400))',
        '{"color":"#2196f3"}'
    );

-- associar geofences ao utilizador
INSERT INTO tc_user_geofence (userid, geofenceid)
SELECT @userId, id
FROM tc_geofences
WHERE name IN ('Depot','Factory');


-- =====================================================
-- 3. GERAR POSIÇÕES SIMULADAS (MOVIMENTO)
-- =====================================================

SET @start = NOW() - INTERVAL 4 HOUR;

INSERT INTO tc_positions
(deviceid, protocol, servertime, devicetime, fixtime,
 valid,
 latitude, longitude,
 altitude, speed, course,
 attributes)

SELECT
    d.id,
    'simulator',
    DATE_ADD(@start, INTERVAL seq MINUTE),
    DATE_ADD(@start, INTERVAL seq MINUTE),
    DATE_ADD(@start, INTERVAL seq MINUTE),

    1,

    38.720 + (RAND()*0.03),
    -9.150 + (RAND()*0.04),

    10,
    30 + RAND()*40,
    RAND()*360,

    '{"ignition":true}'

FROM tc_devices d
         JOIN (
    SELECT @row:=@row+1 AS seq
    FROM information_schema.columns,(SELECT @row:=0) r
    LIMIT 240
) seqs
WHERE d.uniqueid LIKE 'SIMU5%';


-- =====================================================
-- 4. PARAGENS (IGNITION OFF)
-- =====================================================

-- Paragem 1: 15 minutos no Depot
INSERT INTO tc_positions
(deviceid, protocol, servertime, devicetime, fixtime,
 valid,
 latitude, longitude,
 altitude, speed, course,
 attributes)

SELECT
    d.id,
    'simulator',
    DATE_ADD(@start, INTERVAL (60 + seq) MINUTE),
    DATE_ADD(@start, INTERVAL (60 + seq) MINUTE),
    DATE_ADD(@start, INTERVAL (60 + seq) MINUTE),

    1,

    38.7275,
    -9.1425,

    10,
    0,
    0,

    '{"ignition":true}'

FROM tc_devices d
         JOIN (
    SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
) stops
WHERE d.uniqueid LIKE 'SIMU5%';

-- Paragem 2: 15 minutos na Factory
INSERT INTO tc_positions
(deviceid, protocol, servertime, devicetime, fixtime,
 valid,
 latitude, longitude,
 altitude, speed, course,
 attributes)

SELECT
    d.id,
    'simulator',
    DATE_ADD(@start, INTERVAL (120 + seq) MINUTE),
    DATE_ADD(@start, INTERVAL (120 + seq) MINUTE),
    DATE_ADD(@start, INTERVAL (120 + seq) MINUTE),

    1,

    38.7425,
    -9.1725,

    10,
    0,
    0,

    '{"ignition":true}'

FROM tc_devices d
         JOIN (
    SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
) stops
WHERE d.uniqueid LIKE 'SIMU5%';


-- =====================================================
-- 5. ATUALIZAR POSIÇÃO ATUAL DOS DEVICES
-- =====================================================

UPDATE tc_devices d
SET positionid = (
    SELECT id
    FROM tc_positions p
    WHERE p.deviceid = d.id
    ORDER BY p.devicetime DESC
    LIMIT 1
)
WHERE d.uniqueid LIKE 'SIMU5%';

drop database traccar;
create database traccar;