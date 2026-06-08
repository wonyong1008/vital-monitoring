package com.aitrics.vitalmonitoring.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InferenceRequest(
        @NotBlank(message = "환자 ID는 필수입니다.")
        String patient_id
) {}
