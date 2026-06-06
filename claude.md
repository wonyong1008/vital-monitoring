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

<!-- 이후 설계 결정 포인트마다 항목 추가 -->
