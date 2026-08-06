package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("schema_version")
public class SchemaVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String tableName;
    private Integer versionNo;
    private String structureJson;
    private String mappingJson;
    private String batchId;
    private LocalDateTime createdAt;
}
