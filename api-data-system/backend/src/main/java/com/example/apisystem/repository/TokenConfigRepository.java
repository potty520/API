package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.TokenConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenConfigRepository extends BaseMapper<TokenConfig> {
}
