package com.example.apisystem.controller;

import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.entity.FieldMapping;
import com.example.apisystem.service.FieldMappingService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/field-mapping")
public class FieldMappingController {

    private final FieldMappingService fieldMappingService;

    public FieldMappingController(FieldMappingService fieldMappingService) {
        this.fieldMappingService = fieldMappingService;
    }

    @GetMapping("/list/{apiConfigId}")
    public ApiResponse<List<FieldMapping>> getFieldMappings(@PathVariable Long apiConfigId) {
        List<FieldMapping> mappings = fieldMappingService.findByApiConfigId(apiConfigId);
        return ApiResponse.success(mappings);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> saveFieldMappings(@RequestBody List<FieldMapping> mappings,
                                                  @RequestParam Long apiConfigId) {
        boolean result = fieldMappingService.saveFieldMappings(apiConfigId, mappings);
        return ApiResponse.success("Field mappings saved successfully", result);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> deleteFieldMapping(@PathVariable Long id) {
        boolean result = fieldMappingService.removeById(id);
        return ApiResponse.success("Field mapping deleted successfully", result);
    }
}
