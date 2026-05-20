package com.example.apisystem.scheduler;

import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.service.ScheduledTaskService;
import com.example.apisystem.service.TaskExecutorService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Configuration
public class TaskSchedulerConfig implements SchedulingConfigurer {

    private final ScheduledTaskService scheduledTaskService;
    private final TaskExecutorService taskExecutorService;

    private ThreadPoolTaskScheduler taskScheduler;
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public TaskSchedulerConfig(ScheduledTaskService scheduledTaskService,
                              TaskExecutorService taskExecutorService) {
        this.scheduledTaskService = scheduledTaskService;
        this.taskExecutorService = taskExecutorService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

    private ThreadPoolTaskScheduler taskScheduler() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        taskScheduler.setThreadNamePrefix("task-scheduler-");
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setAwaitTerminationSeconds(60);
        taskScheduler.initialize();
        return taskScheduler;
    }

    @PostConstruct
    public void init() {
        loadAndScheduleTasks();
    }

    public void loadAndScheduleTasks() {
        List<ScheduledTask> tasks = scheduledTaskService.findEnabledTasks();
        for (ScheduledTask task : tasks) {
            scheduleTask(task);
        }
    }

    public void scheduleTask(ScheduledTask task) {
        if (scheduledTasks.containsKey(task.getId())) {
            cancelTask(task.getId());
        }

        try {
            if (task.getCronExpression() == null || task.getCronExpression().trim().isEmpty()) {
                return;
            }

            if (!isValidCronExpression(task.getCronExpression())) {
                System.err.println("Invalid cron expression for task " + task.getId() + ": " + task.getCronExpression());
                return;
            }

            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTaskRunnable(task.getId()),
                new CronTrigger(task.getCronExpression())
            );

            scheduledTasks.put(task.getId(), future);
        } catch (Exception e) {
            System.err.println("Failed to schedule task " + task.getId() + ": " + e.getMessage());
        }
    }

    private boolean isValidCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            return false;
        }
        String[] parts = cronExpression.trim().split("\\s+");
        return parts.length == 6;
    }

    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void rescheduleTask(Long taskId) {
        ScheduledTask task = scheduledTaskService.findById(taskId);
        if (task != null && "ENABLED".equals(task.getEnableStatus())) {
            scheduleTask(task);
        } else {
            cancelTask(taskId);
        }
    }

    private void executeTaskRunnable(Long taskId) {
        try {
            taskExecutorService.executeTask(taskId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isTaskRunning(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.get(taskId);
        return future != null && !future.isCancelled() && !future.isDone();
    }
}
