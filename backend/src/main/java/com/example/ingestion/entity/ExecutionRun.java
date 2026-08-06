package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("execution_run")
public class ExecutionRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runNo;
    private Long taskId;
    private String triggerType;
    private String syncMode;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
    private Integer httpStatus;
    private Long responseMs;
    private Integer attemptCount;
    private Long totalCount;
    private Long insertedCount;
    private Long updatedCount;
    private Long skippedCount;
    private Long failedCount;
    private Long emptyCount;
    private String batchId;
    private String targetTable;
    private String schemaChanges;
    private String errorMessage;
    private String errorStack;
    private String requestedBy;
    private String detailJson;
}
