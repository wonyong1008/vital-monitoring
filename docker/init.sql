CREATE TABLE IF NOT EXISTS patients (
    patient_id  VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    gender      VARCHAR(1)   NOT NULL COMMENT 'M / F',
    birth_date  DATE         NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (patient_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS vitals (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id  VARCHAR(20)  NOT NULL,
    recorded_at DATETIME(6)  NOT NULL,
    vital_type  VARCHAR(10)  NOT NULL COMMENT 'HR / RR / SBP / DBP / SpO2 / BT',
    value       DOUBLE       NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vital_composite (patient_id, recorded_at, vital_type),
    CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- ============================================================
-- 기초 데이터 (테스트용 시드)
-- Inference API는 현재 시각 기준 24시간 이내 데이터를 사용하므로
-- recorded_at을 NOW() 기준 상대값으로 삽입합니다.
--
-- 환자별 예상 Inference 결과:
--   P00001234 홍길동 → HIGH   (HR>120, SBP<90, SpO2<90 모두 충족)
--   P00005678 김영희 → MEDIUM (HR>120만 충족)
--   P00009999 이철수 → LOW    (모든 수치 정상)
--   P00000001 박지수 → LOW    (Vital 데이터 없음)
-- ============================================================

INSERT INTO patients (patient_id, name, gender, birth_date, version, created_at, updated_at) VALUES
('P00001234', '홍길동', 'M', '1975-03-01', 1, NOW(), NOW()),
('P00005678', '김영희', 'F', '1990-06-15', 1, NOW(), NOW()),
('P00009999', '이철수', 'M', '1965-11-20', 1, NOW(), NOW()),
('P00000001', '박지수', 'F', '2000-01-10', 1, NOW(), NOW());

-- P00001234 — HIGH risk (HR 평균 126.3 > 120, SBP 평균 83.8 < 90, SpO2 평균 87.5 < 90)
INSERT INTO vitals (patient_id, recorded_at, vital_type, value, version, created_at, updated_at) VALUES
('P00001234', DATE_SUB(NOW(), INTERVAL 20 MINUTE),  'HR',   125.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 20 MINUTE),  'RR',    22.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 20 MINUTE),  'SBP',   82.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 20 MINUTE),  'DBP',   55.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 20 MINUTE),  'SpO2',  86.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 20 MINUTE),  'BT',    38.5, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 1 HOUR),     'HR',   130.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 1 HOUR),     'SBP',   85.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 1 HOUR),     'SpO2',  88.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 2 HOUR),     'HR',   128.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 2 HOUR),     'SBP',   80.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 2 HOUR),     'SpO2',  87.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 4 HOUR),     'HR',   122.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 4 HOUR),     'SBP',   88.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 4 HOUR),     'SpO2',  89.0, 1, NOW(), NOW()),
-- 과거 데이터 (Vital 기간 조회 테스트용 — 7일 전)
('P00001234', DATE_SUB(NOW(), INTERVAL 7 DAY),      'HR',    80.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 7 DAY),      'SBP',  120.0, 1, NOW(), NOW()),
('P00001234', DATE_SUB(NOW(), INTERVAL 7 DAY),      'SpO2',  98.0, 1, NOW(), NOW());

-- P00005678 — MEDIUM risk (HR 평균 123.5 > 120, SBP/SpO2 정상)
INSERT INTO vitals (patient_id, recorded_at, vital_type, value, version, created_at, updated_at) VALUES
('P00005678', DATE_SUB(NOW(), INTERVAL 30 MINUTE),  'HR',   125.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 30 MINUTE),  'RR',    18.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 30 MINUTE),  'SBP',  120.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 30 MINUTE),  'DBP',   75.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 30 MINUTE),  'SpO2',  97.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 30 MINUTE),  'BT',    36.8, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 2 HOUR),     'HR',   122.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 2 HOUR),     'SBP',  115.0, 1, NOW(), NOW()),
('P00005678', DATE_SUB(NOW(), INTERVAL 2 HOUR),     'SpO2',  98.0, 1, NOW(), NOW());

-- P00009999 — LOW risk (모든 수치 정상 범위)
INSERT INTO vitals (patient_id, recorded_at, vital_type, value, version, created_at, updated_at) VALUES
('P00009999', DATE_SUB(NOW(), INTERVAL 45 MINUTE),  'HR',    75.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 45 MINUTE),  'RR',    16.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 45 MINUTE),  'SBP',  118.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 45 MINUTE),  'DBP',   78.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 45 MINUTE),  'SpO2',  98.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 45 MINUTE),  'BT',    36.5, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 3 HOUR),     'HR',    72.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 3 HOUR),     'SBP',  120.0, 1, NOW(), NOW()),
('P00009999', DATE_SUB(NOW(), INTERVAL 3 HOUR),     'SpO2',  99.0, 1, NOW(), NOW());

-- P00000001 — Vital 데이터 없음 (Inference → LOW, 빈 vital_averages)
