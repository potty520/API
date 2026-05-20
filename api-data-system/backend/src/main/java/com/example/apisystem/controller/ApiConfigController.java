package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.entity.ApiConfig;
import com.example.apisystem.service.ApiConfigService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/api-config")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    public ApiConfigController(ApiConfigService apiConfigService) {
        this.apiConfigService = apiConfigService;
    }

    @GetMapping("/list")
    public ApiResponse<Page<ApiConfig>> getApiList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Page<ApiConfig> page = apiConfigService.findPage(pageNum, pageSize, keyword);
        return ApiResponse.success(page);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<ApiConfig> getApiDetail(@PathVariable Long id) {
        ApiConfig apiConfig = apiConfigService.findById(id);
        return ApiResponse.success(apiConfig);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> saveApi(@RequestBody ApiConfig apiConfig) {
        boolean result = apiConfigService.saveApiConfig(apiConfig);
        return ApiResponse.success("API config saved successfully", result);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> deleteApi(@PathVariable Long id) {
        boolean result = apiConfigService.deleteApiConfig(id);
        return ApiResponse.success("API config deleted successfully", result);
    }

    @GetMapping("/by-token/{tokenId}")
    public ApiResponse<List<ApiConfig>> getApisByToken(@PathVariable Long tokenId) {
        List<ApiConfig> apis = apiConfigService.findByTokenConfigId(tokenId);
        return ApiResponse.success(apis);
    }
}
