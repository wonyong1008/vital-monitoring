package com.aitrics.vitalmonitoring.interfaces.dto.response;

import com.aitrics.vitalmonitoring.domain.vital.Vital;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public record VitalsResponse(
        String patient_id,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        VitalType vital_type,

        List<VitalItem> items
) {
    public record VitalItem(
            Instant recorded_at,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            VitalType vital_type,

            Double value
    ) {}

    public static VitalsResponse of(String patientId, VitalType filterType, List<Vital> vitals) {
        List<VitalItem> items = vitals.stream()
                .map(v -> new VitalItem(
                        v.getRecordedAt().toInstant(ZoneOffset.UTC),
                        filterType == null ? v.getVitalType() : null,
                        v.getValue()
                ))
                .toList();
        return new VitalsResponse(patientId, filterType, items);
    }
}
