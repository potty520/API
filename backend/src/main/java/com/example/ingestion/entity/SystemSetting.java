package com.example.ingestion.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("system_setting")
public class SystemSetting {
    @TableId
    private String settingKey;
    private String settingValue;
    private String description;
    private LocalDateTime updatedAt;
}
