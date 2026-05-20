package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.apisystem.entity.FieldMapping;
import com.example.apisystem.repository.FieldMappingRepository;
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
@DisplayName("FieldMappingService")
class FieldMappingServiceTest {

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    private FieldMappingService fieldMappingService;

    @BeforeEach
    void setUp() {
        fieldMappingService = new FieldMappingService();
        ReflectionTestUtils.setField(fieldMappingService, "baseMapper", fieldMappingRepository);
    }

    @Test
    @DisplayName("should find mappings by API config id")
    void findByApiConfigId() {
        FieldMapping m1 = new FieldMapping();
        m1.setSourceField("field1");
        when(fieldMappingRepository.selectList(any(QueryWrapper.class))).thenReturn(Arrays.asList(m1));

        List<FieldMapping> result = fieldMappingService.findByApiConfigId(1L);

        assertEquals(1, result.size());
        assertEquals("field1", result.get(0).getSourceField());
    }

    @Test
    @DisplayName("should batch save field mappings (delete old then insert new)")
    void saveFieldMappings() {
        FieldMapping m1 = new FieldMapping();
        m1.setSourceField("field1");
        m1.setTargetField("target1");
        FieldMapping m2 = new FieldMapping();
        m2.setSourceField("field2");
        m2.setTargetField("target2");

        when(fieldMappingRepository.delete(any(QueryWrapper.class))).thenReturn(1);
        when(fieldMappingRepository.insert(any(FieldMapping.class))).thenReturn(1);

        boolean result = fieldMappingService.saveFieldMappings(1L, Arrays.asList(m1, m2));

        assertTrue(result);
        assertEquals(Long.valueOf(1L), m1.getApiConfigId());
        assertEquals(Long.valueOf(1L), m2.getApiConfigId());
        verify(fieldMappingRepository, times(1)).delete(any(QueryWrapper.class));
        verify(fieldMappingRepository, times(2)).insert(any(FieldMapping.class));
    }

    @Test
    @DisplayName("should delete mappings by API config id")
    void deleteByApiConfigId() {
        when(fieldMappingRepository.delete(any(QueryWrapper.class))).thenReturn(1);

        boolean result = fieldMappingService.deleteByApiConfigId(1L);

        assertTrue(result);
    }
}
