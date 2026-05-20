package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.entity.ApiConfig;
import com.example.apisystem.repository.ApiConfigRepository;
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
@DisplayName("ApiConfigService")
class ApiConfigServiceTest {

    @Mock
    private ApiConfigRepository apiConfigRepository;

    private ApiConfigService apiConfigService;

    @BeforeEach
    void setUp() {
        apiConfigService = spy(new ApiConfigService());
        ReflectionTestUtils.setField(apiConfigService, "baseMapper", apiConfigRepository);
    }

    @Test
    @DisplayName("should find configs by page with keyword filter")
    void findPageWithKeyword() {
        Page<ApiConfig> mockPage = new Page<>(1, 10);
        when(apiConfigRepository.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        Page<ApiConfig> result = apiConfigService.findPage(1, 10, "weather");

        assertNotNull(result);
        verify(apiConfigRepository).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("should find configs by page without keyword")
    void findPageWithoutKeyword() {
        Page<ApiConfig> mockPage = new Page<>(1, 10);
        when(apiConfigRepository.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        Page<ApiConfig> result = apiConfigService.findPage(1, 10, null);

        assertNotNull(result);
        verify(apiConfigRepository).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("should find config by id")
    void findById() {
        ApiConfig config = new ApiConfig();
        config.setId(1L);
        config.setName("Test API");
        when(apiConfigRepository.selectById(1L)).thenReturn(config);

        ApiConfig result = apiConfigService.findById(1L);

        assertEquals("Test API", result.getName());
    }

    @Test
    @DisplayName("should save new config with create time")
    void saveNewConfig() {
        ApiConfig config = new ApiConfig();
        config.setName("New API");
        doReturn(true).when(apiConfigService).saveOrUpdate(any(ApiConfig.class));

        boolean result = apiConfigService.saveApiConfig(config);

        assertTrue(result);
        assertNotNull(config.getCreateTime());
        assertNotNull(config.getUpdateTime());
    }

    @Test
    @DisplayName("should update existing config without overwriting create time")
    void updateExistingConfig() {
        ApiConfig config = new ApiConfig();
        config.setId(1L);
        config.setName("Updated API");
        doReturn(true).when(apiConfigService).saveOrUpdate(any(ApiConfig.class));

        boolean result = apiConfigService.saveApiConfig(config);

        assertTrue(result);
        assertNull(config.getCreateTime());
        assertNotNull(config.getUpdateTime());
    }

    @Test
    @DisplayName("should delete config by id")
    void deleteById() {
        doReturn(true).when(apiConfigService).removeById(1L);

        boolean result = apiConfigService.deleteApiConfig(1L);

        assertTrue(result);
    }

    @Test
    @DisplayName("should find configs by token config id")
    void findByTokenConfigId() {
        ApiConfig config1 = new ApiConfig();
        config1.setName("API 1");
        when(apiConfigRepository.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(config1));

        List<ApiConfig> result = apiConfigService.findByTokenConfigId(1L);

        assertEquals(1, result.size());
        assertEquals("API 1", result.get(0).getName());
    }
}
