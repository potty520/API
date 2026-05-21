package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.FieldMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface FieldMappingRepository extends BaseMapper<FieldMapping> {
    @Select("SELECT * FROM field_mapping WHERE api_config_id = #{apiConfigId} ORDER BY field_order")
    List<FieldMapping> findByApiConfigId(Long apiConfigId);
}
