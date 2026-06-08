package com.aitrics.vitalmonitoring.interfaces.dto.response;

import com.aitrics.vitalmonitoring.domain.vital.Vital;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;

import java.time.Instant;
import java.time.ZoneOffset;

public record VitalUpsertResponse(
        String patient_id,
        Instant recorded_at,
        VitalType vital_type,
        Double value,
        Long version
) {
    public static VitalUpsertResponse from(Vital vital) {
        return new VitalUpsertResponse(
                vital.getPatient().getPatientId(),
                vital.getRecordedAt().toInstant(ZoneOffset.UTC),
                vital.getVitalType(),
                vital.getValue(),
                vital.getVersion()
        );
    }
}
