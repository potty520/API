package com.example.apisystem.controller;

import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.entity.TokenConfig;
import com.example.apisystem.service.TokenConfigService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/token")
public class TokenConfigController {

    private final TokenConfigService tokenConfigService;

    public TokenConfigController(TokenConfigService tokenConfigService) {
        this.tokenConfigService = tokenConfigService;
    }

    @GetMapping("/list")
    public ApiResponse<List<TokenConfig>> getTokenList() {
        List<TokenConfig> tokens = tokenConfigService.findAll();
        return ApiResponse.success(tokens);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<TokenConfig> getTokenDetail(@PathVariable Long id) {
        TokenConfig token = tokenConfigService.findById(id);
        return ApiResponse.success(token);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> saveToken(@RequestBody TokenConfig tokenConfig) {
        boolean result = tokenConfigService.saveTokenConfig(tokenConfig);
        return ApiResponse.success("Token saved successfully", result);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> deleteToken(@PathVariable Long id) {
        boolean result = tokenConfigService.deleteTokenConfig(id);
        return ApiResponse.success("Token deleted successfully", result);
    }

    @PostMapping("/toggle-status/{id}")
    public ApiResponse<Boolean> toggleTokenStatus(@PathVariable Long id, @RequestParam String status) {
        boolean result = tokenConfigService.updateTokenStatus(id, status);
        return ApiResponse.success("Token status updated", result);
    }
}
