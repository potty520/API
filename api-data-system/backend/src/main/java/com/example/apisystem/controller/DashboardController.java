package com.example.apisystem.controller;

import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.dto.DashboardStatsDTO;
import com.example.apisystem.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDTO> getDashboardStats() {
        DashboardStatsDTO stats = dashboardService.getDashboardStats();
        return ApiResponse.success(stats);
    }
}
