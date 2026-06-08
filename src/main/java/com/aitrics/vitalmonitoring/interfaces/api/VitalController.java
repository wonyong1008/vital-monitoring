package com.aitrics.vitalmonitoring.interfaces.api;

import com.aitrics.vitalmonitoring.application.vital.VitalService;
import com.aitrics.vitalmonitoring.domain.vital.VitalType;
import com.aitrics.vitalmonitoring.interfaces.dto.request.VitalUpsertRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.VitalUpsertResponse;
import com.aitrics.vitalmonitoring.interfaces.dto.response.VitalsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@Tag(name = "Vital", description = "Vital 데이터 API")
@SecurityRequirement(name = "bearerAuth")
public class VitalController {

    private final VitalService vitalService;

    @PostMapping("/api/v1/vitals")
    @Operation(summary = "Vital 저장/수정 (UPSERT, Optimistic Lock)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장/수정 성공"),
            @ApiResponse(responseCode = "404", description = "환자 없음"),
            @ApiResponse(responseCode = "409", description = "version 불일치 (동시 수정 충돌)")
    })
    public VitalUpsertResponse upsert(@RequestBody @Valid VitalUpsertRequest request) {
        return vitalService.upsert(request);
    }

    @GetMapping("/api/v1/patients/{patientId}/vitals")
    @Operation(summary = "Vital 데이터 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "환자 없음")
    })
    public VitalsResponse findVitals(
            @PathVariable String patientId,
            @Parameter(description = "조회 시작 시각 (ISO 8601 UTC)", example = "2025-12-01T00:00:00Z")
            @RequestParam Instant from,
            @Parameter(description = "조회 종료 시각 (ISO 8601 UTC)", example = "2025-12-02T00:00:00Z")
            @RequestParam Instant to,
            @Parameter(description = "Vital 타입 필터 (선택)")
            @RequestParam(required = false) VitalType vital_type) {
        return vitalService.findVitals(patientId, from, to, vital_type);
    }
}
