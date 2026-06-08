package com.aitrics.vitalmonitoring.domain.exception;

public class PatientAlreadyExistsException extends RuntimeException {
    public PatientAlreadyExistsException(String patientId) {
        super("이미 등록된 환자입니다: " + patientId);
    }
}
