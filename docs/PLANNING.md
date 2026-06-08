# Vital Monitoring API — 기획 및 설계 문서

## 1. 서비스 개요

병원에서 수집되는 환자의 생체징후(Vital Signs) 데이터를 저장·조회하고,
Rule 기반 위험 스코어를 산출하는 백엔드 API 서버입니다.

온프레미스 환경을 고려하여 환경변수 기반 설정을 지원하며,
의료 데이터 특성상 동시 수정 충돌 방지를 위한 Optimistic Lock을 적용합니다.

---

## 2. 도메인 정의

### Vital 타입

| 타입 | 설명 | 단위 |
|------|------|------|
| HR | 심박수 | bpm |
| RR | 호흡수 | breaths/min |
| SBP | 수축기 혈압 | mmHg |
| DBP | 이완기 혈압 | mmHg |
| SpO2 | 산소포화도 | % |
| BT | 체온 | ℃ |

Vital 데이터는 `(patient_id, recorded_at, vital_type)` 복합 식별자로 유일하게 식별됩니다.

---

## 3. API 목록

### 3-1. 환자 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/patients` | 환자 등록 |
| PUT | `/api/v1/patients/{patient_id}` | 환자 정보 수정 (Optimistic Lock) |

### 3-2. Vital 데이터

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/vitals` | Vital 저장/수정 (UPSERT, Optimistic Lock) |
| GET | `/api/v1/patients/{patient_id}/vitals` | Vital 조회 (기간 필터) |

### 3-3. Inference

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/inference/vital-risk` | 환자 위험 스코어 산출 |

---

## 4. 핵심 설계 결정

### 4-1. Optimistic Lock 적용 지점

동시에 동일한 데이터를 수정하는 경우를 감지하기 위해 version 기반 낙관적 락을 적용합니다.

**적용 대상**
- `PUT /api/v1/patients/{patient_id}` — 환자 정보 수정
- `POST /api/v1/vitals` — 기존 Vital 데이터 UPDATE 시

**동작 방식**
1. 클라이언트는 요청 시 현재 알고 있는 `version`을 함께 전송
2. 서버는 DB의 version과 비교 → 불일치 시 `409 Conflict` 반환
3. JPA `@Version`이 DB 레벨에서 최종 안전망 역할 수행

### 4-2. Vital UPSERT 전략

`(patient_id, recorded_at, vital_type)` 복합 식별자로 기존 레코드를 조회 후:
- 존재하지 않으면 → INSERT (version = 0 으로 시작)
- 존재하면 → version 검증 후 UPDATE

### 4-3. Vitals 테이블 기본키

복합 식별자를 PK로 쓰지 않고 surrogate PK(`id AUTO_INCREMENT`) + 복합 unique constraint 구조를 선택했습니다.
JPA `@Version` Optimistic Lock이 단일 PK 환경에서 명확하게 동작하기 때문입니다.

---

## 5. DB 스키마

```sql
CREATE TABLE patients (
    patient_id  VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    gender      CHAR(1)      NOT NULL,
    birth_date  DATE         NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (patient_id)
);

CREATE TABLE vitals (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id  VARCHAR(20)  NOT NULL,
    recorded_at DATETIME(6)  NOT NULL,
    vital_type  VARCHAR(10)  NOT NULL,
    value       DOUBLE       NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vital_composite (patient_id, recorded_at, vital_type),
    CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id)
);
```

---

## 6. 위험 스코어 산출 규칙

환경변수 `VITAL_RISK_TIME_WINDOW_HOURS`(기본 24시간) 내 데이터를 기준으로 계산합니다.

### 위험 조건

| 조건 | 의미 |
|------|------|
| HR 평균 > 120 | 빈맥 위험 |
| SBP 평균 < 90 | 저혈압 위험 |
| SpO2 평균 < 90 | 산소포화도 저하 위험 |

### 위험 등급

| 충족 조건 수 | 등급 |
|-------------|------|
| 0 | LOW |
| 1 ~ 2 | MEDIUM |
| 3 | HIGH |

---

## 7. 인증

모든 API는 Bearer Token 인증을 요구합니다.

```
Authorization: Bearer <token>
```

토큰은 환경변수 `AUTH_TOKEN`으로 관리하며, 불일치 시 `401 Unauthorized`를 반환합니다.

---

## 8. 아키텍처

DDD-lite 기반 레이어드 아키텍처를 채택했습니다.

```
interfaces/   → Controller, DTO (HTTP 요청/응답)
application/  → Service (유스케이스, 트랜잭션)
domain/       → Entity, Repository 인터페이스, 도메인 예외
infrastructure/ → Repository 구현체 (QueryDSL), Security, 설정
```

도메인 레이어가 인프라에 의존하지 않도록 Repository 인터페이스를 도메인에 정의하고, 구현체는 infrastructure에 위치시켰습니다.
