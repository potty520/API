package com.example.apisystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("field_mapping")
public class FieldMapping {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long apiConfigId;
    private String sourceField;
    private String sourceJsonPath;
    private String targetField;
    private String targetType;
    private Integer isUniqueKey;
    private Integer fieldOrder;
    private String createTime;
    private String updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApiConfigId() { return apiConfigId; }
    public void setApiConfigId(Long apiConfigId) { this.apiConfigId = apiConfigId; }
    public String getSourceField() { return sourceField; }
    public void setSourceField(String sourceField) { this.sourceField = sourceField; }
    public String getSourceJsonPath() { return sourceJsonPath; }
    public void setSourceJsonPath(String sourceJsonPath) { this.sourceJsonPath = sourceJsonPath; }
    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Integer getIsUniqueKey() { return isUniqueKey; }
    public void setIsUniqueKey(Integer isUniqueKey) { this.isUniqueKey = isUniqueKey; }
    public Integer getFieldOrder() { return fieldOrder; }
    public void setFieldOrder(Integer fieldOrder) { this.fieldOrder = fieldOrder; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
