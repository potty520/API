package com.example.apisystem.controller;

import com.example.apisystem.dto.DashboardStatsDTO;
import com.example.apisystem.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@DisplayName("DashboardController")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @DisplayName("GET /api/dashboard/stats should return stats")
    void getStats() throws Exception {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setTotalTasks(10);
        stats.setRunningTasks(5);
        stats.setSuccessRate(90);
        when(dashboardService.getDashboardStats()).thenReturn(stats);

        mockMvc.perform(get("/api/dashboard/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.totalTasks").value(10))
            .andExpect(jsonPath("$.data.runningTasks").value(5))
            .andExpect(jsonPath("$.data.successRate").value(90));
    }
}
