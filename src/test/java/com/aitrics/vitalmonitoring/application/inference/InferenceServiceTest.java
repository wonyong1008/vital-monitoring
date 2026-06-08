package com.aitrics.vitalmonitoring.application.inference;

import com.aitrics.vitalmonitoring.domain.exception.PatientNotFoundException;
import com.aitrics.vitalmonitoring.domain.patient.Patient;
import com.aitrics.vitalmonitoring.domain.patient.PatientRepository;
import com.aitrics.vitalmonitoring.domain.vital.RiskLevel;
import com.aitrics.vitalmonitoring.domain.vital.Vital;
import com.aitrics.vitalmonitoring.domain.vital.VitalRepository;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import com.aitrics.vitalmonitoring.interfaces.dto.request.InferenceRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.InferenceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InferenceServiceTest {

    @InjectMocks
    private InferenceService inferenceService;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private VitalRepository vitalRepository;

    private Patient patient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inferenceService, "timeWindowHours", 24);
        patient = Patient.builder()
                .patientId("P00001234")
                .name("홍길동")
                .gender("M")
                .birthDate(LocalDate.of(1975, 3, 1))
                .build();
    }

    @Test
    @DisplayName("위험 조건 0개 충족 → LOW")
    void evaluate_noRulesTriggered_returnsLow() {
        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndPeriod(eq("P00001234"), any(), any()))
                .willReturn(List.of(
                        vital(VitalType.HR, 80.0),
                        vital(VitalType.SBP, 120.0),
                        vital(VitalType.SpO2, 98.0)
                ));

        InferenceResponse response = inferenceService.evaluate(new InferenceRequest("P00001234"));

        assertThat(response.risk_level()).isEqualTo(RiskLevel.LOW);
        assertThat(response.triggered_rules()).isEmpty();
        assertThat(response.data_points_analyzed()).isEqualTo(3);
    }

    @Test
    @DisplayName("위험 조건 1개 충족 (HR > 120) → MEDIUM")
    void evaluate_oneRuleTriggered_returnsMedium() {
        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndPeriod(eq("P00001234"), any(), any()))
                .willReturn(List.of(
                        vital(VitalType.HR, 130.0),
                        vital(VitalType.SBP, 120.0),
                        vital(VitalType.SpO2, 98.0)
                ));

        InferenceResponse response = inferenceService.evaluate(new InferenceRequest("P00001234"));

        assertThat(response.risk_level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(response.triggered_rules()).containsExactly("HR > 120");
    }

    @Test
    @DisplayName("위험 조건 3개 모두 충족 → HIGH")
    void evaluate_allRulesTriggered_returnsHigh() {
        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndPeriod(eq("P00001234"), any(), any()))
                .willReturn(List.of(
                        vital(VitalType.HR, 135.0),
                        vital(VitalType.SBP, 82.0),
                        vital(VitalType.SpO2, 87.0)
                ));

        InferenceResponse response = inferenceService.evaluate(new InferenceRequest("P00001234"));

        assertThat(response.risk_level()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.triggered_rules()).containsExactlyInAnyOrder("HR > 120", "SBP < 90", "SpO2 < 90");
    }

    @Test
    @DisplayName("일부 Vital 타입 데이터 없어도 존재하는 타입만으로 평가")
    void evaluate_partialVitalData_evaluatesAvailableTypesOnly() {
        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndPeriod(eq("P00001234"), any(), any()))
                .willReturn(List.of(
                        vital(VitalType.HR, 130.0)
                        // SBP, SpO2 데이터 없음
                ));

        InferenceResponse response = inferenceService.evaluate(new InferenceRequest("P00001234"));

        assertThat(response.risk_level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(response.triggered_rules()).containsExactly("HR > 120");
    }

    @Test
    @DisplayName("존재하지 않는 환자 → 404 예외")
    void evaluate_patientNotFound_throws404() {
        given(patientRepository.existsById("P99999999")).willReturn(false);

        assertThatThrownBy(() -> inferenceService.evaluate(new InferenceRequest("P99999999")))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    @DisplayName("평균값이 경계값과 정확히 같을 때 조건 미충족 (HR = 120, SpO2 = 90, SBP = 90)")
    void evaluate_boundaryValues_notTriggered() {
        given(patientRepository.existsById("P00001234")).willReturn(true);
        given(vitalRepository.findByPatientIdAndPeriod(eq("P00001234"), any(), any()))
                .willReturn(List.of(
                        vital(VitalType.HR, 120.0),   // > 120 이어야 위험, = 120은 미충족
                        vital(VitalType.SBP, 90.0),   // < 90 이어야 위험, = 90은 미충족
                        vital(VitalType.SpO2, 90.0)   // < 90 이어야 위험, = 90은 미충족
                ));

        InferenceResponse response = inferenceService.evaluate(new InferenceRequest("P00001234"));

        assertThat(response.risk_level()).isEqualTo(RiskLevel.LOW);
        assertThat(response.triggered_rules()).isEmpty();
    }

    private Vital vital(VitalType type, double value) {
        return Vital.builder()
                .patient(patient)
                .recordedAt(LocalDateTime.now())
                .vitalType(type)
                .value(value)
                .build();
    }
}
