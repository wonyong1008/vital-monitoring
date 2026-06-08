package com.aitrics.vitalmonitoring.application.inference;

import com.aitrics.vitalmonitoring.domain.exception.PatientNotFoundException;
import com.aitrics.vitalmonitoring.domain.vital.RiskLevel;
import com.aitrics.vitalmonitoring.domain.vital.Vital;
import com.aitrics.vitalmonitoring.domain.vital.VitalRepository;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import com.aitrics.vitalmonitoring.domain.patient.PatientRepository;
import com.aitrics.vitalmonitoring.interfaces.dto.request.InferenceRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.InferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InferenceService {

    private final PatientRepository patientRepository;
    private final VitalRepository vitalRepository;

    @Value("${app.inference.vital-risk-time-window-hours}")
    private int timeWindowHours;

    public InferenceResponse evaluate(InferenceRequest request) {
        if (!patientRepository.existsById(request.patient_id())) {
            throw new PatientNotFoundException(request.patient_id());
        }

        Instant now = Instant.now();
        Instant from = now.minusSeconds((long) timeWindowHours * 3600);

        LocalDateTime fromDt = LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        LocalDateTime toDt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        List<Vital> vitals = vitalRepository.findByPatientIdAndPeriod(
                request.patient_id(), fromDt, toDt);

        Map<VitalType, Double> averages = calculateAverages(vitals);
        List<String> triggeredRules = evaluateRules(averages);

        return new InferenceResponse(
                request.patient_id(),
                RiskLevel.from(triggeredRules.size()),
                triggeredRules,
                toStringKeyMap(averages),
                vitals.size(),
                new InferenceResponse.TimeRange(from, now),
                now
        );
    }

    private Map<VitalType, Double> calculateAverages(List<Vital> vitals) {
        return vitals.stream()
                .collect(Collectors.groupingBy(
                        Vital::getVitalType,
                        Collectors.averagingDouble(Vital::getValue)
                ));
    }

    private List<String> evaluateRules(Map<VitalType, Double> averages) {
        List<String> triggered = new ArrayList<>();

        if (averages.containsKey(VitalType.HR) && averages.get(VitalType.HR) > 120) {
            triggered.add("HR > 120");
        }
        if (averages.containsKey(VitalType.SBP) && averages.get(VitalType.SBP) < 90) {
            triggered.add("SBP < 90");
        }
        if (averages.containsKey(VitalType.SpO2) && averages.get(VitalType.SpO2) < 90) {
            triggered.add("SpO2 < 90");
        }

        return triggered;
    }

    private Map<String, Double> toStringKeyMap(Map<VitalType, Double> averages) {
        return averages.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().name(),
                        e -> Math.round(e.getValue() * 10.0) / 10.0
                ));
    }
}
