package com.aitrics.vitalmonitoring.application.patient;

import com.aitrics.vitalmonitoring.domain.exception.OptimisticLockConflictException;
import com.aitrics.vitalmonitoring.domain.exception.PatientAlreadyExistsException;
import com.aitrics.vitalmonitoring.domain.exception.PatientNotFoundException;
import com.aitrics.vitalmonitoring.domain.patient.Patient;
import com.aitrics.vitalmonitoring.domain.patient.PatientRepository;
import com.aitrics.vitalmonitoring.interfaces.dto.request.PatientCreateRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.request.PatientUpdateRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.PatientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @InjectMocks
    private PatientService patientService;

    @Mock
    private PatientRepository patientRepository;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = Patient.builder()
                .patientId("P00001234")
                .name("홍길동")
                .gender("M")
                .birthDate(LocalDate.of(1975, 3, 1))
                .build();
    }

    @Test
    @DisplayName("환자 등록 성공")
    void register_success() {
        PatientCreateRequest request = new PatientCreateRequest("P00001234", "홍길동", "M", LocalDate.of(1975, 3, 1));
        given(patientRepository.existsById("P00001234")).willReturn(false);
        given(patientRepository.save(any(Patient.class))).willReturn(patient);

        PatientResponse response = patientService.register(request);

        assertThat(response.patient_id()).isEqualTo("P00001234");
        assertThat(response.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("이미 존재하는 환자 ID로 등록 시 409 예외 발생")
    void register_duplicatePatientId_throwsConflict() {
        PatientCreateRequest request = new PatientCreateRequest("P00001234", "홍길동", "M", LocalDate.of(1975, 3, 1));
        given(patientRepository.existsById("P00001234")).willReturn(true);

        assertThatThrownBy(() -> patientService.register(request))
                .isInstanceOf(PatientAlreadyExistsException.class);
    }

    @Test
    @DisplayName("환자 정보 수정 성공")
    void update_success() {
        PatientUpdateRequest request = new PatientUpdateRequest("홍길순", "F", LocalDate.of(1975, 3, 1), 0L);
        given(patientRepository.findById("P00001234")).willReturn(Optional.of(patient));
        given(patientRepository.saveAndFlush(any(Patient.class))).willReturn(patient);

        PatientResponse response = patientService.update("P00001234", request);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 환자 수정 시 404 예외 발생")
    void update_patientNotFound_throws404() {
        PatientUpdateRequest request = new PatientUpdateRequest("홍길순", "F", LocalDate.of(1975, 3, 1), 0L);
        given(patientRepository.findById("P99999999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.update("P99999999", request))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    @DisplayName("version 불일치 시 409 예외 발생 (Optimistic Lock)")
    void update_versionMismatch_throws409() {
        PatientUpdateRequest request = new PatientUpdateRequest("홍길순", "F", LocalDate.of(1975, 3, 1), 99L);
        given(patientRepository.findById("P00001234")).willReturn(Optional.of(patient));

        assertThatThrownBy(() -> patientService.update("P00001234", request))
                .isInstanceOf(OptimisticLockConflictException.class);
    }
}
