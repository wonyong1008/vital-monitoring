package com.aitrics.vitalmonitoring.domain.vital;

import com.aitrics.vitalmonitoring.domain.patient.Patient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
    name = "vitals",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_vital_composite",
        columnNames = {"patient_id", "recorded_at", "vital_type"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(10)")
    private VitalType vitalType;

    @Column(nullable = false)
    private Double value;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Vital(Patient patient, LocalDateTime recordedAt, VitalType vitalType, Double value) {
        this.patient = patient;
        this.recordedAt = recordedAt;
        this.vitalType = vitalType;
        this.value = value;
        this.version = 1L;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void update(Double value) {
        this.value = value;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
