package com.aitrics.vitalmonitoring.infrastructure.persistence;

import com.aitrics.vitalmonitoring.domain.vital.QVital;
import com.aitrics.vitalmonitoring.domain.vital.Vital;
import com.aitrics.vitalmonitoring.domain.vital.VitalRepositoryCustom;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VitalRepositoryImpl implements VitalRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private static final QVital vital = QVital.vital;

    @Override
    public Optional<Vital> findByCompositeKey(String patientId, LocalDateTime recordedAt, VitalType vitalType) {
        return Optional.ofNullable(
            queryFactory
                .selectFrom(vital)
                .join(vital.patient).fetchJoin()
                .where(
                    vital.patient.patientId.eq(patientId),
                    vital.recordedAt.eq(recordedAt),
                    vital.vitalType.eq(vitalType)
                )
                .fetchOne()
        );
    }

    @Override
    public List<Vital> findByPatientIdAndPeriod(String patientId, LocalDateTime from, LocalDateTime to) {
        return queryFactory
            .selectFrom(vital)
            .join(vital.patient).fetchJoin()
            .where(
                vital.patient.patientId.eq(patientId),
                vital.recordedAt.between(from, to)
            )
            .orderBy(vital.recordedAt.asc())
            .fetch();
    }

    @Override
    public List<Vital> findByPatientIdAndVitalTypeAndPeriod(String patientId, VitalType vitalType, LocalDateTime from, LocalDateTime to) {
        return queryFactory
            .selectFrom(vital)
            .join(vital.patient).fetchJoin()
            .where(
                vital.patient.patientId.eq(patientId),
                vital.vitalType.eq(vitalType),
                vital.recordedAt.between(from, to)
            )
            .orderBy(vital.recordedAt.asc())
            .fetch();
    }
}
