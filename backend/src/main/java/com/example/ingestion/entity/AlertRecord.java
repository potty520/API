package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long runId;
    private String levelName;
    private String title;
    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
