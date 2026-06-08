# Claude 활용 기록

본 프로젝트에서 Claude를 설계 검토 및 트레이드오프 분석 목적으로 활용하였습니다.

---

## 1. Vitals 테이블 기본키 설계

**Prompt 요약**
> "UPSERT 구현 시 복합 PK vs surrogate PK + unique constraint 중 어떤 게 JPA Optimistic Lock과 더 잘 맞는가?"

**Claude 제안**
- 복합 PK 방식: `(patient_id, recorded_at, vital_type)`을 PK로 사용
- Surrogate PK 방식: `id` AUTO_INCREMENT + 복합 unique constraint

**채택**: Surrogate PK 방식

**이유**
JPA `@Version` 기반 Optimistic Lock은 단일 PK 환경에서 `UPDATE ... WHERE id = ? AND version = ?` 쿼리로 깔끔하게 동작한다.
복합 PK 방식은 JPA `@EmbeddedId`와 `@Version`을 함께 쓸 때 UPSERT 흐름에서 version 초기화 타이밍을 직접 제어해야 하는 복잡도가 생긴다.

---

## 2. Vital 조회 N+1 문제 해결 방안

**Prompt 요약**
> "Vital 목록 조회 시 N+1 문제가 발생할 수 있는 지점은 어디이고, 어떻게 해결하는 게 좋은가?"

**Claude 제안**
- JPQL `@Query` + `JOIN FETCH` 방식
- QueryDSL + `fetchJoin()` 방식

**채택**: QueryDSL 방식

**이유**
JPQL `@Query`로도 N+1 해결은 가능하지만, 조건 조합(기간 필터, vitalType 유무)이 늘어날수록 쿼리 문자열 관리가 어려워진다.
QueryDSL은 타입 안전한 조건 조합과 `fetchJoin()`을 함께 쓸 수 있어 유지보수성이 높다.
또한 `VitalRepositoryCustom` 인터페이스로 분리하여 도메인 계층의 Repository 인터페이스가 인프라 구현에 의존하지 않도록 구성했다.

---

## 3. Optimistic Lock 구현 전략

**Prompt 요약**
> "Patient 수정 시 Optimistic Lock을 순수 JPA @Version에만 맡길지, 명시적 version 검증을 추가할지 어떻게 하는 게 좋은가?"

**Claude 제안**
- 방식 A: 순수 JPA @Version — 명시적 검증 없이 ObjectOptimisticLockingFailureException 발생 시 처리
- 방식 B: 명시적 version 검증 + JPA @Version 안전망 이중 구조

**채택**: 방식 B (이중 구조)

**이유**
방식 A만 사용하면 JPA 예외가 트랜잭션 커밋 시점에 발생해 서비스 레이어에서 명확한 에러 메시지를 담기 어렵다.
방식 B는 명시적 검증으로 대부분의 충돌을 서비스 레이어에서 조기에 잡고, saveAndFlush로 DB 레벨 충돌도 트랜잭션 내에서 감지한다.
GlobalExceptionHandler에도 ObjectOptimisticLockingFailureException 핸들러를 안전망으로 추가하여 누락 없이 409를 반환하도록 했다.

---

## 4. recorded_at 타입 선택 (API 경계)

**Prompt 요약**
> "recorded_at이 ISO 8601 UTC 형식('Z' suffix)으로 들어올 때 DTO 타입을 LocalDateTime vs Instant 중 어떤 걸 써야 하는가?"

**Claude 제안**
- LocalDateTime: Jackson 설정으로 파싱 가능하지만 timezone 정보 손실
- Instant: UTC 기준 시각을 명시적으로 표현, API 경계에서 의미가 명확

**채택**: API DTO는 `Instant`, DB 저장은 `LocalDateTime(UTC)` — 서비스 레이어에서 변환

**이유**
LocalDateTime은 timezone 정보가 없어 "Z"가 붙은 ISO 8601 문자열을 Jackson이 기본 설정으로 파싱하지 못한다.
Instant를 DTO에 쓰면 클라이언트가 보내는 UTC 타임스탬프의 의미가 타입 수준에서 명확해지고,
서비스 레이어에서 `LocalDateTime.ofInstant(instant, ZoneOffset.UTC)`로 변환하여 DB에 저장한다.

---

## 5. Inference 위험 조건 평가 시 데이터 부재 처리

**Prompt 요약**
> "시간 범위 내에 HR, SBP, SpO2 데이터가 없는 경우 위험 조건 평가를 어떻게 처리할지?"

**Claude 제안**
- 방식 A: 데이터 없으면 해당 조건을 미충족으로 처리 (default safe)
- 방식 B: 데이터 없으면 예외 발생

**채택**: 방식 A

**이유**
의료 데이터 특성상 특정 Vital 타입이 측정되지 않았을 수 있다.
데이터 부재를 위험 신호로 보지 않고 조건 미충족으로 처리하는 것이 임상적으로 합리적이며,
`averages.containsKey(vitalType)` 체크로 해당 타입 데이터가 있는 경우에만 조건을 평가한다.

---

<!-- 이후 설계 결정 포인트마다 항목 추가 -->
