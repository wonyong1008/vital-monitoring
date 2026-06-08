package com.aitrics.vitalmonitoring.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record PatientUpdateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotBlank(message = "성별은 필수입니다.")
        @Pattern(regexp = "^[MF]$", message = "성별은 M 또는 F만 허용됩니다.")
        String gender,

        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birth_date,

        @NotNull(message = "version은 필수입니다.")
        Long version
) {}
