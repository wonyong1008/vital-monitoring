package com.aitrics.vitalmonitoring.interfaces.dto.request;

import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record VitalUpsertRequest(
        @NotBlank(message = "환자 ID는 필수입니다.")
        String patient_id,

        @NotNull(message = "측정 시각은 필수입니다.")
        Instant recorded_at,

        @NotNull(message = "Vital 타입은 필수입니다.")
        VitalType vital_type,

        @NotNull(message = "측정값은 필수입니다.")
        @Positive(message = "측정값은 0보다 커야 합니다.")
        Double value,

        @NotNull(message = "version은 필수입니다.")
        Long version
) {}
