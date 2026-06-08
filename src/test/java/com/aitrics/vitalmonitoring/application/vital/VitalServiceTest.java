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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class VitalServiceTest {

    @InjectMocks
    private VitalService vitalService;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private VitalRepository vitalRepository;

    private Patient patient;
    private Instant recordedAt;
    private LocalDateTime recordedAtDt;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .patientId("P00001234")
                .name("홍길동")
                .gender("M")
                .birthDate(LocalDate.of(1975, 3, 1))
                .build();
        recordedAt = Instant.parse("2025-12-01T10:15:00Z");
        recordedAtDt = LocalDateTime.ofInstant(recordedAt, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Vital INSERT 성공 - 동일 복합키 데이터 없을 때")
    void upsert_insert_success() {
        VitalUpsertRequest request = new VitalUpsertRequest("P00001234", recordedAt, VitalType.HR, 100.0, 0L);
        Vital saved = Vital.builder().patient(patient).recordedAt(recordedAtDt).vitalType(VitalType.HR).value(100.0).build();

        given(patientRepository.findById("P00001234")).willReturn(Optional.of(patient));
        given(vitalRepository.findByCompositeKey("P00001234", recordedAtDt, VitalType.HR)).willReturn(Optional.empty());
        given(vitalRepository.save(any(Vital.class))).willReturn(saved);

        VitalUpsertResponse response = vitalService.upsert(request);

        assertThat(response.vital_type()).isEqualTo(VitalType.HR);
        assertThat(response.value()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Vital UPDATE 성공 - 동일 복합키 데이터 존재, version 일치")
    void upsert_update_success() {
        Vital existing = Vital.builder().patient(patient).recordedAt(recordedAtDt).vitalType(VitalType.HR).value(100.0).build();
        VitalUpsertRequest request = new VitalUpsertRequest("P00001234", recordedAt, VitalType.HR, 120.0, 1L);

        given(patientRepository.findById("P00001234")).willReturn(Optional.of(patient));
        given(vitalRepository.findByCompositeKey("P00001234", recordedAtDt, VitalType.HR)).willReturn(Optional.of(existing));
        given(vitalRepository.saveAndFlush(any(Vital.class))).willReturn(existing);

        VitalUpsertResponse response = vitalService.upsert(request);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Vital UPDATE 시 version 불일치 → 409 예외 (Optimistic Lock)")
    void upsert_update_versionMismatch_throws409() {
        Vital existing = Vital.builder().patient(patient).recordedAt(recordedAtDt).vitalType(VitalType.HR).value(100.0).build();
        VitalUpsertRequest request = new VitalUpsertRequest("P00001234", recordedAt, VitalType.HR, 120.0, 99L);

        given(patientRepository.findById("P00001234")).willReturn(Optional.of(patient));
        given(vitalRepository.findByCompositeKey("P00001234", recordedAtDt, VitalType.HR)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> vitalService.upsert(request))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    @DisplayName("등록되지 않은 환자 Vital 저장 시 404 예외 발생")
    void upsert_patientNotFound_throws404() {
        VitalUpsertRequest request = new VitalUpsertRequest("P99999999", recordedAt, VitalType.HR, 100.0, 0L);
        given(patientRepository.findById("P99999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> vitalService.upsert(request))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    @DisplayName("Vital 타입 필터 없이 기간 조회")
    void findVitals_withoutFilter_returnsAll() {
        Vital v1 = Vital.builder().patient(patient).recordedAt(recordedAtDt).vitalType(VitalType.HR).value(100.0).build();
        Vital v2 = Vital.builder().patient(patient).recordedAt(recordedAtDt).vitalType(VitalType.SBP).value(120.0).build();

        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndPeriod(any(), any(), any())).willReturn(List.of(v1, v2));

        VitalsResponse response = vitalService.findVitals("P00001234", recordedAt.minusSeconds(3600), recordedAt, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.vital_type()).isNull();
    }

    @Test
    @DisplayName("Vital 타입 필터 적용 조회")
    void findVitals_withVitalTypeFilter() {
        Vital v1 = Vital.builder().patient(patient).recordedAt(recordedAtDt).vitalType(VitalType.HR).value(100.0).build();

        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndVitalTypeAndPeriod(any(), any(), any(), any())).willReturn(List.of(v1));

        VitalsResponse response = vitalService.findVitals("P00001234", recordedAt.minusSeconds(3600), recordedAt, VitalType.HR);

        assertThat(response.vital_type()).isEqualTo(VitalType.HR);
        assertThat(response.items()).hasSize(1);
    }
}
