package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.FieldMapping;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FieldMappingRepository extends BaseMapper<FieldMapping> {
    List<FieldMapping> findByApiConfigId(Long apiConfigId);
}
