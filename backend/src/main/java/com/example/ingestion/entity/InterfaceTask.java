package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("interface_task")
public class InterfaceTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Long groupId;
    private String url;
    private String method;
    private String description;
    private Boolean enabled;
    private String status;
    private String headersJson;
    private String queryJson;
    private String bodyJson;
    private String authType;
    private String authJsonEnc;
    private String rootPath;
    private Long datasourceId;
    private String tableName;
    private String uniqueKey;
    private String cronExpr;
    private Integer retryCount;
    private Integer retryIntervalSec;
    private Integer timeoutSec;
    private String sourceLabel;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private LocalDateTime lastSuccessAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
