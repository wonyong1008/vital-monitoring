package com.aitrics.vitalmonitoring.interfaces.dto.response;

import com.aitrics.vitalmonitoring.domain.patient.Patient;

import java.time.LocalDate;

public record PatientResponse(
        String patient_id,
        String name,
        String gender,
        LocalDate birth_date,
        Long version
) {
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getPatientId(),
                patient.getName(),
                patient.getGender(),
                patient.getBirthDate(),
                patient.getVersion()
        );
    }
}
