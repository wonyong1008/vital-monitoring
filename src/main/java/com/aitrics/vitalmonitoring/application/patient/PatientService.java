package com.aitrics.vitalmonitoring.application.patient;

import com.aitrics.vitalmonitoring.domain.exception.OptimisticLockConflictException;
import com.aitrics.vitalmonitoring.domain.exception.PatientAlreadyExistsException;
import com.aitrics.vitalmonitoring.domain.exception.PatientNotFoundException;
import com.aitrics.vitalmonitoring.domain.patient.Patient;
import com.aitrics.vitalmonitoring.domain.patient.PatientRepository;
import com.aitrics.vitalmonitoring.interfaces.dto.request.PatientCreateRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.request.PatientUpdateRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.PatientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientResponse register(PatientCreateRequest request) {
        if (patientRepository.existsById(request.patient_id())) {
            throw new PatientAlreadyExistsException(request.patient_id());
        }
        Patient patient = Patient.builder()
                .patientId(request.patient_id())
                .name(request.name())
                .gender(request.gender())
                .birthDate(request.birth_date())
                .build();
        return PatientResponse.from(patientRepository.save(patient));
    }

    public PatientResponse update(String patientId, PatientUpdateRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        // 명시적 version 검증 — 클라이언트가 보낸 version과 DB version 비교
        if (!patient.getVersion().equals(request.version())) {
            throw new OptimisticLockConflictException("데이터가 이미 수정되었습니다. 최신 데이터를 다시 확인해주세요.");
        }

        try {
            patient.update(request.name(), request.gender(), request.birth_date());
            // saveAndFlush로 즉시 반영하여 @Version 충돌을 트랜잭션 내에서 감지
            return PatientResponse.from(patientRepository.saveAndFlush(patient));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("동시 수정으로 인한 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }
}
