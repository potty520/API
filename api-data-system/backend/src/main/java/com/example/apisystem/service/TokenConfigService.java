package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.apisystem.entity.TokenConfig;
import com.example.apisystem.repository.TokenConfigRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TokenConfigService extends ServiceImpl<TokenConfigRepository, TokenConfig> {

    public List<TokenConfig> findAll() {
        return this.list();
    }

    public TokenConfig findById(Long id) {
        return this.getById(id);
    }

    public boolean saveTokenConfig(TokenConfig tokenConfig) {
        if (tokenConfig.getId() == null) {
            tokenConfig.setCreateTime(LocalDateTime.now());
        }
        tokenConfig.setUpdateTime(LocalDateTime.now());
        return this.saveOrUpdate(tokenConfig);
    }

    public boolean deleteTokenConfig(Long id) {
        return this.removeById(id);
    }

    public List<TokenConfig> findActiveTokens() {
        QueryWrapper<TokenConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "ACTIVE");
        return this.list(wrapper);
    }

    public boolean updateTokenStatus(Long id, String status) {
        TokenConfig tokenConfig = this.getById(id);
        if (tokenConfig != null) {
            tokenConfig.setStatus(status);
            tokenConfig.setUpdateTime(LocalDateTime.now());
            return this.updateById(tokenConfig);
        }
        return false;
    }
}
