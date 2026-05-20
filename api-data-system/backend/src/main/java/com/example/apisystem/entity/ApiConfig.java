package com.example.apisystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("api_config")
public class ApiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long tokenConfigId;
    private String apiUrl;
    private String apiMethod;
    private String apiParams;
    private String apiHeaders;
    private String dataExtractPath;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getTokenConfigId() { return tokenConfigId; }
    public void setTokenConfigId(Long tokenConfigId) { this.tokenConfigId = tokenConfigId; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getApiMethod() { return apiMethod; }
    public void setApiMethod(String apiMethod) { this.apiMethod = apiMethod; }
    public String getApiParams() { return apiParams; }
    public void setApiParams(String apiParams) { this.apiParams = apiParams; }
    public String getApiHeaders() { return apiHeaders; }
    public void setApiHeaders(String apiHeaders) { this.apiHeaders = apiHeaders; }
    public String getDataExtractPath() { return dataExtractPath; }
    public void setDataExtractPath(String dataExtractPath) { this.dataExtractPath = dataExtractPath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
