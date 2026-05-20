package com.example.apisystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("token_config")
public class TokenConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String tokenType;
    private String fixedToken;
    private String tokenUrl;
    private String tokenMethod;
    private String tokenParams;
    private String tokenHeaders;
    private String tokenExtractPath;
    private String expiresInPath;
    private Integer defaultExpiresSeconds;
    private Integer refreshBeforeMinutes;
    private String status;
    private String currentToken;
    private LocalDateTime tokenExpireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public String getFixedToken() { return fixedToken; }
    public void setFixedToken(String fixedToken) { this.fixedToken = fixedToken; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getTokenMethod() { return tokenMethod; }
    public void setTokenMethod(String tokenMethod) { this.tokenMethod = tokenMethod; }
    public String getTokenParams() { return tokenParams; }
    public void setTokenParams(String tokenParams) { this.tokenParams = tokenParams; }
    public String getTokenHeaders() { return tokenHeaders; }
    public void setTokenHeaders(String tokenHeaders) { this.tokenHeaders = tokenHeaders; }
    public String getTokenExtractPath() { return tokenExtractPath; }
    public void setTokenExtractPath(String tokenExtractPath) { this.tokenExtractPath = tokenExtractPath; }
    public String getExpiresInPath() { return expiresInPath; }
    public void setExpiresInPath(String expiresInPath) { this.expiresInPath = expiresInPath; }
    public Integer getDefaultExpiresSeconds() { return defaultExpiresSeconds; }
    public void setDefaultExpiresSeconds(Integer defaultExpiresSeconds) { this.defaultExpiresSeconds = defaultExpiresSeconds; }
    public Integer getRefreshBeforeMinutes() { return refreshBeforeMinutes; }
    public void setRefreshBeforeMinutes(Integer refreshBeforeMinutes) { this.refreshBeforeMinutes = refreshBeforeMinutes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentToken() { return currentToken; }
    public void setCurrentToken(String currentToken) { this.currentToken = currentToken; }
    public LocalDateTime getTokenExpireTime() { return tokenExpireTime; }
    public void setTokenExpireTime(LocalDateTime tokenExpireTime) { this.tokenExpireTime = tokenExpireTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
