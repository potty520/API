package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.DatabaseConnection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatabaseConnectionRepository extends BaseMapper<DatabaseConnection> {
}
