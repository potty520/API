package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String actionName;
    private String moduleName;
    private String detailText;
    private String ipAddress;
    private LocalDateTime createdAt;
    private String prevHash;
    private String rowHash;
}