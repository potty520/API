package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.entity.TaskExecutionLog;
import com.example.apisystem.service.TaskExecutionLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskExecutionLogController.class)
@DisplayName("TaskExecutionLogController")
class TaskExecutionLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskExecutionLogService taskExecutionLogService;

    @Test
    @DisplayName("GET /api/log/list should return paginated logs")
    void getLogList() throws Exception {
        Page<TaskExecutionLog> page = new Page<>(1, 10);
        page.setRecords(Arrays.asList());
        when(taskExecutionLogService.findPage(isNull(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/log/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("GET /api/log/detail/{id} should return single log")
    void getLogDetail() throws Exception {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(1L);
        log.setBatchId("TASK_20260520_ABC");
        when(taskExecutionLogService.findById(1L)).thenReturn(log);

        mockMvc.perform(get("/api/log/detail/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.batchId").value("TASK_20260520_ABC"));
    }

    @Test
    @DisplayName("GET /api/log/recent should return recent logs")
    void getRecentLogs() throws Exception {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(1L);
        when(taskExecutionLogService.findRecentLogs(10)).thenReturn(Arrays.asList(log));

        mockMvc.perform(get("/api/log/recent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }
}
