package com.example.apisystem.controller;

import com.example.apisystem.entity.TokenConfig;
import com.example.apisystem.service.TokenConfigService;
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

@WebMvcTest(TokenConfigController.class)
@DisplayName("TokenConfigController")
class TokenConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenConfigService tokenConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/token/list should return token list")
    void getTokenList() throws Exception {
        TokenConfig token = new TokenConfig();
        token.setId(1L);
        token.setName("Test Token");
        when(tokenConfigService.findAll()).thenReturn(Arrays.asList(token));

        mockMvc.perform(get("/api/token/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data[0].name").value("Test Token"));
    }

    @Test
    @DisplayName("GET /api/token/detail/{id} should return single token")
    void getTokenDetail() throws Exception {
        TokenConfig token = new TokenConfig();
        token.setId(1L);
        token.setName("Detail Token");
        when(tokenConfigService.findById(1L)).thenReturn(token);

        mockMvc.perform(get("/api/token/detail/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.name").value("Detail Token"));
    }

    @Test
    @DisplayName("POST /api/token/save should save token")
    void saveToken() throws Exception {
        TokenConfig token = new TokenConfig();
        token.setName("New Token");
        when(tokenConfigService.saveTokenConfig(any(TokenConfig.class))).thenReturn(true);

        mockMvc.perform(post("/api/token/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /api/token/delete/{id} should delete token")
    void deleteToken() throws Exception {
        when(tokenConfigService.deleteTokenConfig(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/token/delete/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
