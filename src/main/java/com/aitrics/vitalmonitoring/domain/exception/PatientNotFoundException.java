package com.aitrics.vitalmonitoring.domain.exception;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(String patientId) {
        super("환자를 찾을 수 없습니다: " + patientId);
    }
}
