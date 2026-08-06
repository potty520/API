package com.example.ingestion.scheduler;

import com.example.ingestion.common.ApiException;
import com.example.ingestion.ingest.SyncEngine;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DisallowConcurrentExecution
public class IngestionJob implements Job {
    private SyncEngine engine;

    @Autowired
    public void setEngine(SyncEngine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long taskId = context.getMergedJobDataMap().getLong("taskId");
        try {
            engine.execute(taskId, new SyncEngine.ExecuteOptions("schedule", false, "scheduler"));
        } catch (ApiException error) {
            if (error.getStatus() == 409) {
                log.info("Task {} is already running; duplicate schedule was blocked", taskId);
                return;
            }
            throw new JobExecutionException(error, false);
        }
    }
}
