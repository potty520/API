package com.example.apisystem.controller;

import com.example.apisystem.entity.FieldMapping;
import com.example.apisystem.service.FieldMappingService;
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

@WebMvcTest(FieldMappingController.class)
@DisplayName("FieldMappingController")
class FieldMappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FieldMappingService fieldMappingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/field-mapping/list/{apiConfigId} should return mappings")
    void getFieldMappings() throws Exception {
        FieldMapping m1 = new FieldMapping();
        m1.setSourceField("field1");
        when(fieldMappingService.findByApiConfigId(1L)).thenReturn(Arrays.asList(m1));

        mockMvc.perform(get("/api/field-mapping/list/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].sourceField").value("field1"));
    }

    @Test
    @DisplayName("POST /api/field-mapping/save should batch save mappings")
    void saveFieldMappings() throws Exception {
        FieldMapping m1 = new FieldMapping();
        m1.setSourceField("field1");
        when(fieldMappingService.saveFieldMappings(anyLong(), anyList())).thenReturn(true);

        mockMvc.perform(post("/api/field-mapping/save?apiConfigId=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Arrays.asList(m1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
