package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.dto.TaskScheduleDTO;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.scheduler.TaskSchedulerConfig;
import com.example.apisystem.service.ApiConfigService;
import com.example.apisystem.service.ScheduledTaskService;
import com.example.apisystem.service.TaskExecutorService;
import com.example.apisystem.service.CronExpressionParser;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/task")
public class TaskSchedulerController {

    private final ScheduledTaskService scheduledTaskService;
    private final ApiConfigService apiConfigService;
    private final TaskExecutorService taskExecutorService;
    private final TaskSchedulerConfig taskSchedulerConfig;

    public TaskSchedulerController(ScheduledTaskService scheduledTaskService,
                                   ApiConfigService apiConfigService,
                                   TaskExecutorService taskExecutorService,
                                   TaskSchedulerConfig taskSchedulerConfig) {
        this.scheduledTaskService = scheduledTaskService;
        this.apiConfigService = apiConfigService;
        this.taskExecutorService = taskExecutorService;
        this.taskSchedulerConfig = taskSchedulerConfig;
    }

    @GetMapping("/list")
    public ApiResponse<Page<ScheduledTask>> getTaskList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Page<ScheduledTask> page = scheduledTaskService.findPage(pageNum, pageSize, keyword);
        return ApiResponse.success(page);
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<ScheduledTask> getTaskDetail(@PathVariable Long id) {
        ScheduledTask task = scheduledTaskService.findById(id);
        return ApiResponse.success(task);
    }

    @PostMapping("/save")
    public ApiResponse<Boolean> saveTask(@RequestBody ScheduledTask task) {
        boolean result = scheduledTaskService.saveTask(task);
        if (result) {
            taskSchedulerConfig.rescheduleTask(task.getId());
        }
        return ApiResponse.success("Task saved successfully", result);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Boolean> deleteTask(@PathVariable Long id) {
        taskSchedulerConfig.cancelTask(id);
        boolean result = scheduledTaskService.deleteTask(id);
        return ApiResponse.success("Task deleted successfully", result);
    }

    @PostMapping("/toggle/{id}")
    public ApiResponse<Boolean> toggleTaskStatus(@PathVariable Long id) {
        boolean result = scheduledTaskService.toggleTaskStatus(id);
        taskSchedulerConfig.rescheduleTask(id);
        return ApiResponse.success("Task status updated", result);
    }

    @PostMapping("/execute/{id}")
    public ApiResponse<String> executeTask(@PathVariable Long id) {
        try {
            new Thread(() -> taskExecutorService.executeTask(id)).start();
            return ApiResponse.success("Task execution started");
        } catch (Exception e) {
            return ApiResponse.error("Failed to execute task: " + e.getMessage());
        }
    }

    @GetMapping("/validate-cron")
    public ApiResponse<Boolean> validateCron(@RequestParam String cronExpression) {
        CronExpressionParser parser = new CronExpressionParser();
        boolean isValid = parser.isValid(cronExpression);
        return ApiResponse.success(isValid);
    }

    @GetMapping("/cron-next-time")
    public ApiResponse<String> getCronNextTime(@RequestParam String cronExpression) {
        String nextTime = taskExecutorService.getNextExecuteTime(cronExpression);
        return ApiResponse.success(nextTime);
    }

    @GetMapping("/cron-description")
    public ApiResponse<String> getCronDescription(@RequestParam String cronExpression) {
        CronExpressionParser parser = new CronExpressionParser();
        String description = parser.describe(cronExpression);
        return ApiResponse.success(description);
    }

    @GetMapping("/enabled")
    public ApiResponse<List<ScheduledTask>> getEnabledTasks() {
        List<ScheduledTask> tasks = scheduledTaskService.findEnabledTasks();
        return ApiResponse.success(tasks);
    }

    @GetMapping("/is-running/{id}")
    public ApiResponse<Boolean> isTaskRunning(@PathVariable Long id) {
        boolean isRunning = taskSchedulerConfig.isTaskRunning(id);
        return ApiResponse.success(isRunning);
    }
}
