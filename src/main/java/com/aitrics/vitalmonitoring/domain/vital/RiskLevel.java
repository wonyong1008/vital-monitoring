package com.aitrics.vitalmonitoring.domain.vital;

public enum RiskLevel {
    LOW, MEDIUM, HIGH;

    public static RiskLevel from(int triggeredCount) {
        if (triggeredCount == 0) return LOW;
        if (triggeredCount < 3) return MEDIUM;
        return HIGH;
    }
}
