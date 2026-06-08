# Vital Monitoring API

환자 생체징후(Vital Signs) 데이터를 저장·조회하고 Rule 기반 위험 스코어를 산출하는 백엔드 API 서버입니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| ORM | Spring Data JPA + QueryDSL 5.0 |
| DB | MySQL 8.0 |
| 인증 | JWT (Access Token + Refresh Token) |
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

---

## 실행 방법

### 1. Docker Compose로 실행

```bash
# 프로젝트 루트에서 실행
docker compose -f docker/docker-compose.yml up --build
```

MySQL + 앱이 함께 실행됩니다. 첫 실행 시 이미지 빌드로 2~3분 소요됩니다.

- 애플리케이션: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

종료:
```bash
docker compose -f docker/docker-compose.yml down
```

DB 데이터 초기화 후 종료:
```bash
docker compose -f docker/docker-compose.yml down -v
```

### 2. 테스트 실행

```bash
./gradlew test
```

---

## 인증 방법

모든 API는 JWT Bearer Token 인증이 필요합니다.

### Step 1 — 로그인

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "1234"}'
```

응답:
```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### Step 2 — API 호출

```bash
curl -X GET http://localhost:8080/api/v1/patients/P00001234/vitals \
  -H "Authorization: Bearer eyJ..."
```

### Step 3 — Access Token 재발급

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "eyJ..."}'
```

### Swagger UI에서 인증

1. http://localhost:8080/swagger-ui.html 접속
2. 우상단 **Authorize** 🔒 클릭
3. 로그인 API 호출 후 받은 `access_token` 값 입력
4. Authorize 클릭

---

## 환경변수

`.env.example`을 참고하여 설정합니다.

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `DB_URL` | JDBC URL | — |
| `DB_USERNAME` | DB 사용자 | — |
| `DB_PASSWORD` | DB 패스워드 | — |
| `AUTH_USERNAME` | 로그인 아이디 | admin |
| `AUTH_PASSWORD` | 로그인 패스워드 | 1234 |
| `JWT_SECRET` | JWT 서명 키 (Base64) | 기본값 있음 |
| `JWT_ACCESS_TOKEN_EXPIRY_MS` | Access Token 만료 (ms) | 3600000 (1시간) |
| `JWT_REFRESH_TOKEN_EXPIRY_MS` | Refresh Token 만료 (ms) | 604800000 (7일) |
| `VITAL_RISK_TIME_WINDOW_HOURS` | Inference 조회 시간 범위 | 24 |

---

## API 목록

| Method | Endpoint | 인증 | 설명 |
|--------|----------|------|------|
| POST | `/api/v1/auth/login` | 불필요 | 로그인 (Access + Refresh Token 발급) |
| POST | `/api/v1/auth/refresh` | 불필요 | Access Token 재발급 |
| POST | `/api/v1/patients` | 필요 | 환자 등록 |
| PUT | `/api/v1/patients/{patient_id}` | 필요 | 환자 정보 수정 (Optimistic Lock) |
| POST | `/api/v1/vitals` | 필요 | Vital 저장/수정 UPSERT (Optimistic Lock) |
| GET | `/api/v1/patients/{patient_id}/vitals` | 필요 | Vital 조회 |
| POST | `/api/v1/inference/vital-risk` | 필요 | 위험 스코어 산출 |

---

## DB 스키마

```sql
CREATE TABLE patients (
    patient_id  VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    gender      VARCHAR(1)   NOT NULL COMMENT 'M / F',
    birth_date  DATE         NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 1,
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
    version     BIGINT       NOT NULL DEFAULT 1,
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

INSERT 시 version은 `1`로 시작하고, 매 UPDATE마다 JPA가 자동으로 `+1` 증가시킵니다.

---

## AI 활용 기록

`claude.md` 파일 참조
