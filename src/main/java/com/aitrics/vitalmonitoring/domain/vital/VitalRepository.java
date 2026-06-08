package com.aitrics.vitalmonitoring.domain.vital;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalRepository extends JpaRepository<Vital, Long>, VitalRepositoryCustom {
}
