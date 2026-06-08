package com.aitrics.vitalmonitoring.interfaces.dto.response;

import com.aitrics.vitalmonitoring.domain.vital.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record InferenceResponse(
        String patient_id,
        RiskLevel risk_level,
        List<String> triggered_rules,
        Map<String, Double> vital_averages,
        int data_points_analyzed,
        TimeRange time_range,
        Instant evaluated_at
) {
    public record TimeRange(Instant from, Instant to) {}
}
