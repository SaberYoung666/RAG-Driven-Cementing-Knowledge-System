package org.swpu.backend.modules.rag.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swpu.backend.common.api.ApiResponse;
import org.swpu.backend.modules.rag.service.RagAvailabilityService;

@RestController
@RequestMapping("/api/v1/rag")
public class RagStatusController {
    private final RagAvailabilityService ragAvailabilityService;

    public RagStatusController(RagAvailabilityService ragAvailabilityService) {
        this.ragAvailabilityService = ragAvailabilityService;
    }

    @GetMapping("/status")
    public ApiResponse<RagAvailabilityService.RagStatus> status() {
        return ApiResponse.success(ragAvailabilityService.getStatus());
    }
}
