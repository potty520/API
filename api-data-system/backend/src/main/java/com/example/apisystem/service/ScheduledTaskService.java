package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.entity.TaskExecutionLog;
import com.example.apisystem.repository.ScheduledTaskRepository;
import com.example.apisystem.repository.TaskExecutionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledTaskService extends ServiceImpl<ScheduledTaskRepository, ScheduledTask> {

    private final TaskExecutionLogRepository taskExecutionLogRepository;

    public ScheduledTaskService(TaskExecutionLogRepository taskExecutionLogRepository) {
        this.taskExecutionLogRepository = taskExecutionLogRepository;
    }

    public Page<ScheduledTask> findPage(int pageNum, int pageSize, String keyword) {
        Page<ScheduledTask> page = new Page<>(pageNum, pageSize);
        QueryWrapper<ScheduledTask> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("task_name", keyword).or().like("task_code", keyword);
        }
        wrapper.orderByDesc("create_time");
        return this.page(page, wrapper);
    }

    public ScheduledTask findById(Long id) {
        return this.getById(id);
    }

    @Transactional
    public boolean saveTask(ScheduledTask task) {
        if (task.getId() == null) {
            task.setCreateTime(LocalDateTime.now());
            task.setExecuteCount(0);
            task.setSuccessCount(0);
            task.setFailCount(0);
        }
        task.setUpdateTime(LocalDateTime.now());
        return this.saveOrUpdate(task);
    }

    public boolean deleteTask(Long id) {
        return this.removeById(id);
    }

    public boolean toggleTaskStatus(Long id) {
        ScheduledTask task = this.getById(id);
        if (task != null) {
            task.setEnableStatus("ENABLED".equals(task.getEnableStatus()) ? "DISABLED" : "ENABLED");
            task.setUpdateTime(LocalDateTime.now());
            return this.updateById(task);
        }
        return false;
    }

    public List<ScheduledTask> findEnabledTasks() {
        QueryWrapper<ScheduledTask> wrapper = new QueryWrapper<>();
        wrapper.eq("enable_status", "ENABLED");
        return this.list(wrapper);
    }

    @Transactional
    public void updateTaskExecutionStats(Long taskId, String status, long duration, int totalRecords, int newInsertCount, int existingCount) {
        ScheduledTask task = this.getById(taskId);
        if (task != null) {
            task.setLastExecuteTime(LocalDateTime.now());
            task.setLastDuration(duration);
            task.setExecuteCount(task.getExecuteCount() + 1);

            if ("SUCCESS".equals(status)) {
                task.setSuccessCount(task.getSuccessCount() + 1);
            } else {
                task.setFailCount(task.getFailCount() + 1);
            }

            this.updateById(task);
        }
    }

    public List<ScheduledTask> findRecentFailedTasks(int limit) {
        QueryWrapper<ScheduledTask> wrapper = new QueryWrapper<>();
        wrapper.eq("enable_status", "ENABLED");
        wrapper.last("ORDER BY update_time DESC LIMIT " + limit);
        return this.list(wrapper);
    }
}
