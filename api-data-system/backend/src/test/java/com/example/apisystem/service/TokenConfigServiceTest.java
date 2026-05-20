package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.apisystem.entity.TokenConfig;
import com.example.apisystem.repository.TokenConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenConfigService")
class TokenConfigServiceTest {

    @Mock
    private TokenConfigRepository tokenConfigRepository;

    private TokenConfigService tokenConfigService;

    @BeforeEach
    void setUp() {
        tokenConfigService = spy(new TokenConfigService());
        ReflectionTestUtils.setField(tokenConfigService, "baseMapper", tokenConfigRepository);
    }

    @Test
    @DisplayName("should find all token configs")
    void findAll() {
        TokenConfig token = new TokenConfig();
        token.setName("Test Token");
        when(tokenConfigRepository.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(token));

        List<TokenConfig> result = tokenConfigService.findAll();

        assertEquals(1, result.size());
        assertEquals("Test Token", result.get(0).getName());
    }

    @Test
    @DisplayName("should save new token with create time")
    void saveNewToken() {
        TokenConfig token = new TokenConfig();
        token.setName("New Token");
        token.setTokenType("FIXED");
        doReturn(true).when(tokenConfigService).saveOrUpdate(any(TokenConfig.class));

        boolean result = tokenConfigService.saveTokenConfig(token);

        assertTrue(result);
        assertNotNull(token.getCreateTime());
        assertNotNull(token.getUpdateTime());
    }

    @Test
    @DisplayName("should update existing token without overwriting create time")
    void updateExistingToken() {
        TokenConfig token = new TokenConfig();
        token.setId(1L);
        token.setName("Updated Token");
        doReturn(true).when(tokenConfigService).saveOrUpdate(any(TokenConfig.class));

        boolean result = tokenConfigService.saveTokenConfig(token);

        assertTrue(result);
        assertNull(token.getCreateTime());
        assertNotNull(token.getUpdateTime());
    }

    @Test
    @DisplayName("should delete token by id")
    void deleteById() {
        doReturn(true).when(tokenConfigService).removeById(1L);

        boolean result = tokenConfigService.deleteTokenConfig(1L);

        assertTrue(result);
    }

    @Test
    @DisplayName("should find active tokens")
    void findActiveTokens() {
        TokenConfig active = new TokenConfig();
        active.setStatus("ACTIVE");
        when(tokenConfigRepository.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(active));

        List<TokenConfig> result = tokenConfigService.findActiveTokens();

        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    @DisplayName("should update token status successfully")
    void updateTokenStatusSuccess() {
        TokenConfig token = new TokenConfig();
        token.setId(1L);
        token.setStatus("ACTIVE");
        when(tokenConfigRepository.selectById(1L)).thenReturn(token);
        doReturn(true).when(tokenConfigService).updateById(any(TokenConfig.class));

        boolean result = tokenConfigService.updateTokenStatus(1L, "INACTIVE");

        assertTrue(result);
        assertEquals("INACTIVE", token.getStatus());
        assertNotNull(token.getUpdateTime());
    }

    @Test
    @DisplayName("should return false when updating status of non-existent token")
    void updateTokenStatusNotFound() {
        when(tokenConfigRepository.selectById(999L)).thenReturn(null);

        boolean result = tokenConfigService.updateTokenStatus(999L, "INACTIVE");

        assertFalse(result);
    }
}
