package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.apisystem.entity.FieldMapping;
import com.example.apisystem.repository.FieldMappingRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class FieldMappingService extends ServiceImpl<FieldMappingRepository, FieldMapping> {

    public List<FieldMapping> findByApiConfigId(Long apiConfigId) {
        QueryWrapper<FieldMapping> wrapper = new QueryWrapper<>();
        wrapper.eq("api_config_id", apiConfigId);
        wrapper.orderByAsc("field_order");
        return this.list(wrapper);
    }

    public boolean saveFieldMapping(FieldMapping fieldMapping) {
        return this.saveOrUpdate(fieldMapping);
    }

    public boolean deleteByApiConfigId(Long apiConfigId) {
        QueryWrapper<FieldMapping> wrapper = new QueryWrapper<>();
        wrapper.eq("api_config_id", apiConfigId);
        return this.remove(wrapper);
    }

    public boolean saveFieldMappings(Long apiConfigId, List<FieldMapping> mappings) {
        QueryWrapper<FieldMapping> wrapper = new QueryWrapper<>();
        wrapper.eq("api_config_id", apiConfigId);
        this.remove(wrapper);

        for (FieldMapping mapping : mappings) {
            mapping.setApiConfigId(apiConfigId);
            this.save(mapping);
        }
        return true;
    }
}
