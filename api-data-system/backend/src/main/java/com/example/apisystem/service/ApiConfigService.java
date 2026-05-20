package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.apisystem.entity.ApiConfig;
import com.example.apisystem.repository.ApiConfigRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApiConfigService extends ServiceImpl<ApiConfigRepository, ApiConfig> {

    public Page<ApiConfig> findPage(int pageNum, int pageSize, String keyword) {
        Page<ApiConfig> page = new Page<>(pageNum, pageSize);
        QueryWrapper<ApiConfig> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("name", keyword).or().like("description", keyword);
        }
        wrapper.orderByDesc("create_time");
        return this.page(page, wrapper);
    }

    public ApiConfig findById(Long id) {
        return this.getById(id);
    }

    public boolean saveApiConfig(ApiConfig apiConfig) {
        if (apiConfig.getId() == null) {
            apiConfig.setCreateTime(LocalDateTime.now());
        }
        apiConfig.setUpdateTime(LocalDateTime.now());
        return this.saveOrUpdate(apiConfig);
    }

    public boolean deleteApiConfig(Long id) {
        return this.removeById(id);
    }

    public List<ApiConfig> findByTokenConfigId(Long tokenConfigId) {
        QueryWrapper<ApiConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("token_config_id", tokenConfigId);
        return this.list(wrapper);
    }
}
