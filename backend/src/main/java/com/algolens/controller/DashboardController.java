package com.algolens.controller;

import com.algolens.dto.DashboardResponse;
import com.algolens.security.AuthUser;
import com.algolens.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Counts and recent activity for the signed-in user")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthUser user) {
        return dashboardService.forUser(user.id());
    }
}
