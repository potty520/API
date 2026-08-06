package com.example.ingestion.config;

import com.example.ingestion.scheduler.AutowiringJobFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {
    @Bean
    SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(AutowireCapableBeanFactory beanFactory) {
        return factory -> {
            factory.setJobFactory(new AutowiringJobFactory(beanFactory));
            factory.setWaitForJobsToCompleteOnShutdown(true);
            factory.setOverwriteExistingJobs(true);
        };
    }
}
