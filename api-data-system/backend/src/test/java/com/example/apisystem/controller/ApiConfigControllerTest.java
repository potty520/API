package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.entity.ApiConfig;
import com.example.apisystem.service.ApiConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiConfigController.class)
@DisplayName("ApiConfigController")
class ApiConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiConfigService apiConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/api-config/list should return paginated list")
    void getApiList() throws Exception {
        Page<ApiConfig> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList());
        when(apiConfigService.findPage(anyInt(), anyInt(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/api-config/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("GET /api/api-config/detail/{id} should return single API config")
    void getApiDetail() throws Exception {
        ApiConfig config = new ApiConfig();
        config.setId(1L);
        config.setName("Test API");
        when(apiConfigService.findById(1L)).thenReturn(config);

        mockMvc.perform(get("/api/api-config/detail/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("Test API"));
    }

    @Test
    @DisplayName("POST /api/api-config/save should save API config")
    void saveApi() throws Exception {
        ApiConfig config = new ApiConfig();
        config.setName("New API");
        when(apiConfigService.saveApiConfig(any(ApiConfig.class))).thenReturn(true);

        mockMvc.perform(post("/api/api-config/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(config)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /api/api-config/delete/{id} should delete API config")
    void deleteApi() throws Exception {
        when(apiConfigService.deleteApiConfig(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/api-config/delete/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
