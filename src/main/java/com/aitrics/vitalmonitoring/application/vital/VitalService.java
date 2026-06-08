package com.aitrics.vitalmonitoring.application.vital;

import com.aitrics.vitalmonitoring.domain.exception.OptimisticLockConflictException;
import com.aitrics.vitalmonitoring.domain.exception.PatientNotFoundException;
import com.aitrics.vitalmonitoring.domain.patient.Patient;
import com.aitrics.vitalmonitoring.domain.patient.PatientRepository;
import com.aitrics.vitalmonitoring.domain.vital.Vital;
import com.aitrics.vitalmonitoring.domain.vital.VitalRepository;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import com.aitrics.vitalmonitoring.interfaces.dto.request.VitalUpsertRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.VitalUpsertResponse;
import com.aitrics.vitalmonitoring.interfaces.dto.response.VitalsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VitalService {

    private final PatientRepository patientRepository;
    private final VitalRepository vitalRepository;

    public VitalUpsertResponse upsert(VitalUpsertRequest request) {
        Patient patient = patientRepository.findById(request.patient_id())
                .orElseThrow(() -> new PatientNotFoundException(request.patient_id()));

        LocalDateTime recordedAt = LocalDateTime.ofInstant(request.recorded_at(), ZoneOffset.UTC);

        Optional<Vital> existing = vitalRepository.findByCompositeKey(
                request.patient_id(), recordedAt, request.vital_type());

        if (existing.isEmpty()) {
            return insert(patient, recordedAt, request);
        }
        return update(existing.get(), request);
    }

    @Transactional(readOnly = true)
    public VitalsResponse findVitals(String patientId, Instant from, Instant to, VitalType vitalType) {
        if (!patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException(patientId);
        }

        LocalDateTime fromDt = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        LocalDateTime toDt = LocalDateTime.ofInstant(to, ZoneOffset.UTC);

        List<Vital> vitals = vitalType != null
                ? vitalRepository.findByPatientIdAndVitalTypeAndPeriod(patientId, vitalType, fromDt, toDt)
                : vitalRepository.findByPatientIdAndPeriod(patientId, fromDt, toDt);

        return VitalsResponse.of(patientId, vitalType, vitals);
    }

    private VitalUpsertResponse insert(Patient patient, LocalDateTime recordedAt, VitalUpsertRequest request) {
        Vital vital = Vital.builder()
                .patient(patient)
                .recordedAt(recordedAt)
                .vitalType(request.vital_type())
                .value(request.value())
                .build();
        return VitalUpsertResponse.from(patient.getPatientId(), vitalRepository.save(vital));
    }

    private VitalUpsertResponse update(Vital vital, VitalUpsertRequest request) {
        if (!vital.getVersion().equals(request.version())) {
            throw new OptimisticLockConflictException("데이터가 이미 수정되었습니다. 최신 데이터를 다시 확인해주세요.");
        }
        try {
            vital.update(request.value());
            return VitalUpsertResponse.from(request.patient_id(), vitalRepository.saveAndFlush(vital));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException("동시 수정으로 인한 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }
}
