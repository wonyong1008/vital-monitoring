package com.aitrics.vitalmonitoring.interfaces.api;

import com.aitrics.vitalmonitoring.application.inference.InferenceService;
import com.aitrics.vitalmonitoring.interfaces.dto.request.InferenceRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.InferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inference")
@RequiredArgsConstructor
@Tag(name = "Inference", description = "위험 스코어 산출 API")
@SecurityRequirement(name = "bearerAuth")
public class InferenceController {

    private final InferenceService inferenceService;

    @PostMapping("/vital-risk")
    @Operation(summary = "환자 위험 스코어 산출")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "산출 성공"),
            @ApiResponse(responseCode = "404", description = "환자 없음")
    })
    public InferenceResponse evaluate(@RequestBody @Valid InferenceRequest request) {
        return inferenceService.evaluate(request);
    }
}
