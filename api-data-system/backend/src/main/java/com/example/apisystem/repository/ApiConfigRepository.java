package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.ApiConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiConfigRepository extends BaseMapper<ApiConfig> {
}
