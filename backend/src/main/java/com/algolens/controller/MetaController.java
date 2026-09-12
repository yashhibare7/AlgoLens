package com.algolens.controller;

import com.algolens.dto.MetaResponse;
import com.algolens.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
@Tag(name = "Meta")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @GetMapping
    @Operation(summary = "Capabilities, limits and prices, read by the frontend at boot")
    public MetaResponse meta() {
        return metaService.describe();
    }

    @GetMapping("/health")
    @Operation(summary = "Liveness check that needs no authentication")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "algolens-backend");
    }
}
