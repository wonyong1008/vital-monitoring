package com.aitrics.vitalmonitoring.domain.vital;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VitalRepositoryCustom {

    Optional<Vital> findByCompositeKey(String patientId, LocalDateTime recordedAt, VitalType vitalType);

    List<Vital> findByPatientIdAndPeriod(String patientId, LocalDateTime from, LocalDateTime to);

    List<Vital> findByPatientIdAndVitalTypeAndPeriod(String patientId, VitalType vitalType, LocalDateTime from, LocalDateTime to);
}
