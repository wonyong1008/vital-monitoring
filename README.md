# Vital Monitoring API

환자 생체징후(Vital Signs) 데이터를 저장·조회하고 Rule 기반 위험 스코어를 산출하는 백엔드 API 서버입니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| ORM | Spring Data JPA + QueryDSL 5.0 |
| DB | MySQL 8.0 (로컬 개발: H2 in-memory) |
| 인증 | Bearer Token (환경변수 관리) |
| API 문서 | Springdoc OpenAPI (Swagger UI) |
| 빌드 | Gradle |
| 컨테이너 | Docker, Docker Compose |

---

## 아키텍처

DDD-lite 기반 레이어드 아키텍처를 적용했습니다.

```
com.aitrics.vitalmonitoring
├── interfaces/        # Controller, DTO (HTTP 요청/응답 처리)
├── application/       # Service (유스케이스, 트랜잭션 경계)
├── domain/            # Entity, Repository 인터페이스, 도메인 예외
└── infrastructure/    # Repository 구현체(QueryDSL), Security, 설정
```

도메인 레이어가 인프라에 의존하지 않도록 Repository 인터페이스를 도메인에 정의하고, 구현체는 infrastructure에 위치시켰습니다.

---

## 실행 방법

### Docker Compose (권장)

```bash
# 프로젝트 루트에서 실행
docker compose -f docker/docker-compose.yml up --build
```

- 애플리케이션: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

### 로컬 실행 (H2 in-memory)

```bash
./gradlew bootRun
```

별도 DB 설정 없이 H2 in-memory로 동작합니다. 기본 Bearer Token: `dev-token-change-in-prod`

### 환경변수 설정

`.env.example`을 참고하여 `.env` 파일을 생성합니다.

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `DB_URL` | JDBC URL | H2 in-memory |
| `DB_USERNAME` | DB 사용자 | sa |
| `DB_PASSWORD` | DB 패스워드 | (없음) |
| `AUTH_TOKEN` | Bearer 인증 토큰 | dev-token-change-in-prod |
| `VITAL_RISK_TIME_WINDOW_HOURS` | Inference 조회 시간 범위 (시간) | 24 |

---

## API 목록

Swagger UI에서 전체 명세 확인: `http://localhost:8080/swagger-ui.html`

모든 API는 아래 헤더가 필요합니다.
```
Authorization: Bearer <token>
```

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/patients` | 환자 등록 |
| PUT | `/api/v1/patients/{patient_id}` | 환자 정보 수정 (Optimistic Lock) |
| POST | `/api/v1/vitals` | Vital 저장/수정 UPSERT (Optimistic Lock) |
| GET | `/api/v1/patients/{patient_id}/vitals` | Vital 조회 |
| POST | `/api/v1/inference/vital-risk` | 위험 스코어 산출 |

---

## DB 스키마

```sql
CREATE TABLE patients (
    patient_id  VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    gender      VARCHAR(1)   NOT NULL COMMENT 'M / F',
    birth_date  DATE         NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE vitals (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id  VARCHAR(20)  NOT NULL,
    recorded_at DATETIME(6)  NOT NULL,
    vital_type  VARCHAR(10)  NOT NULL COMMENT 'HR / RR / SBP / DBP / SpO2 / BT',
    value       DOUBLE       NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_vital_composite (patient_id, recorded_at, vital_type),
    CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

---

## Optimistic Lock 설계 및 동작

### 적용 지점

| API | 적용 조건 |
|-----|-----------|
| `PUT /api/v1/patients/{patient_id}` | 항상 |
| `POST /api/v1/vitals` | 기존 레코드 UPDATE 시 |

### 동작 방식

```
클라이언트                      서버                         DB
    │                            │                            │
    │── 수정 요청 (version: N) ──▶│                            │
    │                            │── SELECT (version 조회) ──▶│
    │                            │◀─ version = N ─────────────│
    │                            │                            │
    │                            │  [명시적 검증]             │
    │                            │  request.version == N → OK  │
    │                            │                            │
    │                            │── UPDATE WHERE version = N ▶│
    │                            │◀─ affected rows = 1 ────────│
    │                            │   (version → N+1 자동 증가) │
    │◀── 200 OK (version: N+1) ──│                            │
```

### version 불일치 시나리오

```
클라이언트 A (version: 1)  클라이언트 B (version: 1)     DB (version: 1)
        │                           │                        │
        │── 수정 요청 ──────────────▶│                        │
        │                           │── 수정 요청 ───────────▶│
        │                           │                        │
        │◀── 200 OK (version: 2) ───│  DB version: 2         │
        │                           │── UPDATE WHERE v=1 ───▶│ affected = 0
        │                           │◀── 409 Conflict ───────│
```

### 구현 전략

1. **명시적 version 검증** — 서비스 레이어에서 request.version == db.version 비교 → 불일치 시 즉시 `409 Conflict`
2. **JPA `@Version` 안전망** — `saveAndFlush()`로 DB 레벨 충돌도 트랜잭션 내에서 감지
3. **GlobalExceptionHandler** — `ObjectOptimisticLockingFailureException` 핸들러로 최종 안전망

INSERT 시 version은 `0`으로 시작하고, 매 UPDATE마다 JPA가 자동으로 `+1` 증가시킵니다.

---

## 테스트 실행

```bash
./gradlew test
```

Service 레이어 단위 테스트 (Mockito 기반):
- `PatientServiceTest` — 등록/수정, Optimistic Lock 충돌 시나리오
- `VitalServiceTest` — UPSERT INSERT/UPDATE, version 불일치
- `InferenceServiceTest` — 위험 등급 산출 (LOW/MEDIUM/HIGH), 경계값, 데이터 부재

---

## AI 활용 기록

`claude.md` 파일 참조
