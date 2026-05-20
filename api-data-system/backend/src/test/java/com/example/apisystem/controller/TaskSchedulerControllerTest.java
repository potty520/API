package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.entity.ScheduledTask;
import com.example.apisystem.scheduler.TaskSchedulerConfig;
import com.example.apisystem.service.ApiConfigService;
import com.example.apisystem.service.ScheduledTaskService;
import com.example.apisystem.service.TaskExecutorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TaskSchedulerController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = TaskSchedulerConfig.class))
@DisplayName("TaskSchedulerController")
class TaskSchedulerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduledTaskService scheduledTaskService;

    @MockBean
    private ApiConfigService apiConfigService;

    @MockBean
    private TaskExecutorService taskExecutorService;

    @MockBean
    private TaskSchedulerConfig taskSchedulerConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/task/list should return paginated tasks")
    void getTaskList() throws Exception {
        Page<ScheduledTask> page = new Page<>(1, 10);
        when(scheduledTaskService.findPage(anyInt(), anyInt(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/task/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/task/detail/{id} should return single task")
    void getTaskDetail() throws Exception {
        ScheduledTask task = new ScheduledTask();
        task.setId(1L);
        task.setTaskName("Test Task");
        when(scheduledTaskService.findById(1L)).thenReturn(task);

        mockMvc.perform(get("/api/task/detail/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.taskName").value("Test Task"));
    }

    @Test
    @DisplayName("POST /api/task/save should save task")
    void saveTask() throws Exception {
        ScheduledTask task = new ScheduledTask();
        task.setTaskName("New Task");
        when(scheduledTaskService.saveTask(any(ScheduledTask.class))).thenReturn(true);

        mockMvc.perform(post("/api/task/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(task)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/task/cron-description should describe cron")
    void getCronDescription() throws Exception {
        when(taskExecutorService.getNextExecuteTime(anyString())).thenReturn("2026-05-20 12:00:00");

        mockMvc.perform(get("/api/task/cron-description?cronExpression=0 */5 * * *"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/task/execute/{id} should trigger execution")
    void executeTask() throws Exception {
        mockMvc.perform(post("/api/task/execute/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value("Task execution started"));
    }

    @Test
    @DisplayName("GET /api/task/enabled should return enabled tasks")
    void getEnabledTasks() throws Exception {
        when(scheduledTaskService.findEnabledTasks()).thenReturn(java.util.Arrays.asList());

        mockMvc.perform(get("/api/task/enabled"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }
}
