package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("data_source_config")
public class DataSourceConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String schemaName;
    private String serviceName;
    private String jdbcUrl;
    private String username;
    private String passwordEnc;
    private String optionsJson;
    private Boolean active;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
