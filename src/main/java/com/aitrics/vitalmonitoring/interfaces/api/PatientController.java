package com.aitrics.vitalmonitoring.interfaces.api;

import com.aitrics.vitalmonitoring.application.patient.PatientService;
import com.aitrics.vitalmonitoring.interfaces.dto.request.PatientCreateRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.request.PatientUpdateRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.PatientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Patient", description = "환자 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class PatientController {

    private final PatientService patientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "환자 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 환자 ID")
    })
    public PatientResponse register(@RequestBody @Valid PatientCreateRequest request) {
        return patientService.register(request);
    }

    @PutMapping("/{patientId}")
    @Operation(summary = "환자 정보 수정 (Optimistic Lock)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "환자 없음"),
            @ApiResponse(responseCode = "409", description = "version 불일치 (동시 수정 충돌)")
    })
    public PatientResponse update(@PathVariable String patientId,
                                  @RequestBody @Valid PatientUpdateRequest request) {
        return patientService.update(patientId, request);
    }
}
