package com.example.ingestion.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.entity.InterfaceTask;
import com.example.ingestion.entity.SystemSetting;
import com.example.ingestion.mapper.InterfaceTaskMapper;
import com.example.ingestion.mapper.SystemSettingMapper;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
public class TaskSchedulerService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final Scheduler scheduler;
    private final InterfaceTaskMapper tasks;
    private final SystemSettingMapper settings;

    public TaskSchedulerService(Scheduler scheduler, InterfaceTaskMapper tasks, SystemSettingMapper settings) {
        this.scheduler = scheduler;
        this.tasks = tasks;
        this.settings = settings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        tasks.selectList(Wrappers.<InterfaceTask>lambdaQuery().eq(InterfaceTask::getEnabled, true)).forEach(task -> {
            try { schedule(task); }
            catch (Exception error) {
                task.setStatus("Cron无效");
                task.setNextRunAt(null);
                task.setUpdatedAt(LocalDateTime.now());
                tasks.updateById(task);
                log.error("Failed to restore task {} schedule", task.getId(), error);
            }
        });
    }

    public void schedule(InterfaceTask task) throws SchedulerException {
        delete(task.getId());
        if (!Boolean.TRUE.equals(task.getEnabled()) || !globallyEnabled()) {
            task.setNextRunAt(null);
            tasks.updateById(task);
            return;
        }
        String cron = CronSupport.normalize(task.getCronExpr());
        if (!CronSupport.valid(cron)) throw new IllegalArgumentException("Cron 表达式无效");
        JobDataMap data = new JobDataMap();
        data.put("taskId", task.getId());
        JobDetail job = JobBuilder.newJob(IngestionJob.class).withIdentity(jobKey(task.getId())).usingJobData(data).build();
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(cron)
                .inTimeZone(java.util.TimeZone.getTimeZone(ZONE)).withMisfireHandlingInstructionDoNothing();
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey(task.getId()))
                .forJob(job).withSchedule(schedule).build();
        scheduler.scheduleJob(job, trigger);
        task.setCronExpr(cron);
        task.setNextRunAt(toLocal(trigger.getNextFireTime()));
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
    }

    public void delete(Long taskId) throws SchedulerException {
        JobKey key = jobKey(taskId);
        if (scheduler.checkExists(key)) scheduler.deleteJob(key);
    }

    public LocalDateTime nextRun(Long taskId) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(taskId));
            return trigger == null ? null : toLocal(trigger.getNextFireTime());
        } catch (SchedulerException error) {
            return null;
        }
    }

    public boolean globallyEnabled() {
        SystemSetting value = settings.selectById("scheduler_enabled");
        return value == null || !"false".equalsIgnoreCase(value.getSettingValue());
    }

    private JobKey jobKey(Long id) { return JobKey.jobKey("task-" + id, "ingestion"); }
    private TriggerKey triggerKey(Long id) { return TriggerKey.triggerKey("task-" + id, "ingestion"); }
    private LocalDateTime toLocal(Date date) { return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZONE); }
}
