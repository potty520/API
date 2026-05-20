package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.entity.TaskExecutionLog;
import com.example.apisystem.service.TaskExecutionLogService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/log")
public class TaskExecutionLogController {

    private final TaskExecutionLogService taskExecutionLogService;

    public TaskExecutionLogController(TaskExecutionLogService taskExecutionLogService) {
        this.taskExecutionLogService = taskExecutionLogService;
    }

    @GetMapping("/list")
    public ApiResponse<Page<TaskExecutionLog>> getLogList(
            @RequestParam(required = false) Long taskId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<TaskExecutionLog> page = taskExecutionLogService.findPage(taskId, pageNum, pageSize);
        return ApiResponse.success(page);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<TaskExecutionLog> getLogDetail(@PathVariable Long id) {
        TaskExecutionLog log = taskExecutionLogService.findById(id);
        return ApiResponse.success(log);
    }

    @GetMapping("/recent")
    public ApiResponse<List<TaskExecutionLog>> getRecentLogs(@RequestParam(defaultValue = "10") int limit) {
        List<TaskExecutionLog> logs = taskExecutionLogService.findRecentLogs(limit);
        return ApiResponse.success(logs);
    }

    @GetMapping("/recent-failed")
    public ApiResponse<List<TaskExecutionLog>> getRecentFailedLogs(@RequestParam(defaultValue = "10") int limit) {
        List<TaskExecutionLog> logs = taskExecutionLogService.findRecentFailedLogs(limit);
        return ApiResponse.success(logs);
    }
}
