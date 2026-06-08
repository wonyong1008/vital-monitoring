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

## 6. application 레이어의 interfaces DTO 의존 (인지된 트레이드오프)

**코드 리뷰에서 발견된 설계 이슈**
PatientService, VitalService 등 application 레이어가 interfaces 레이어의 Request/Response DTO를 직접 참조하고 있다.
순수 DDD에서는 application 레이어가 자체 Command/Result 객체를 가져야 한다.

**미적용 이유**
3~6시간 분량의 과제에서 Command 객체를 별도로 두면 불필요한 boilerplate가 증가한다.
현재 서비스가 단일 애플리케이션으로 동작하며, interfaces DTO가 곧 유스케이스 입력과 동일한 구조이므로 DDD-lite 범위 내 허용 가능한 트레이드오프로 판단했다.
실제 프로덕션 환경에서 멀티 채널(REST + gRPC 등)이 추가된다면 Command 분리가 필요하다.

---

## 7. VitalUpsertResponse에서 Patient 연관관계 접근 방식

**코드 리뷰에서 발견된 이슈**
> "`VitalUpsertResponse.from(vital)`에서 `vital.getPatient().getPatientId()`를 호출하는데, Patient가 `@ManyToOne(LAZY)`라 트랜잭션 외부에서 호출 시 LazyInitializationException이 발생할 수 있다."

**Claude 제안**
- 방식 A: `@ManyToOne(fetch = EAGER)`로 변경
- 방식 B: `from()` 메서드 시그니처를 `from(String patientId, Vital vital)`로 변경해 연관관계에 의존하지 않도록 분리

**채택**: 방식 B

**이유**
EAGER 로딩은 Vital 조회 시 항상 Patient를 JOIN하게 되어 불필요한 쿼리가 발생한다.
patientId는 서비스 레이어에서 이미 알고 있는 값이므로, 직접 전달하는 방식이 안전하고 명확하다.

---

## 8. Hibernate 6의 @Enumerated(EnumType.STRING) 타입 매핑 변경

**실행 중 발견된 이슈**
> "Schema-validation: found [varchar], but expecting [enum] — Hibernate 6에서 `@Enumerated(EnumType.STRING)`이 MySQL ENUM 타입을 기대하도록 동작이 바뀌었다."

**Claude 제안**
- 방식 A: DDL을 MySQL ENUM 타입으로 변경
- 방식 B: 엔티티에 `@Column(columnDefinition = "VARCHAR(10)")`을 명시해 VARCHAR 강제

**채택**: 방식 B

**이유**
MySQL ENUM은 값 추가 시 ALTER TABLE이 필요해 운영 환경에서 부담이 크다.
VARCHAR로 저장하면 VitalType 변경에 유연하게 대응할 수 있고, columnDefinition 명시로 Hibernate 6의 기본 동작을 명확하게 오버라이드할 수 있다.

---

<!-- 이후 설계 결정 포인트마다 항목 추가 -->
