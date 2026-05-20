package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.TaskExecutionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TaskExecutionLogRepository extends BaseMapper<TaskExecutionLog> {

    @Select("SELECT * FROM task_execution_log WHERE task_id = #{taskId} ORDER BY create_time DESC LIMIT #{limit}")
    List<TaskExecutionLog> findRecentLogsByTaskId(Long taskId, int limit);

    @Select("SELECT * FROM task_execution_log WHERE execute_status = 'FAILED' ORDER BY create_time DESC LIMIT #{limit}")
    List<TaskExecutionLog> findRecentFailedLogs(int limit);

    @Select("SELECT COUNT(*) FROM task_execution_log WHERE execute_status = 'SUCCESS' AND create_time >= CURDATE()")
    Integer countTodaySuccessExecutions();

    @Select("SELECT COUNT(*) FROM task_execution_log WHERE execute_status = 'FAILED' AND create_time >= CURDATE()")
    Integer countTodayFailedExecutions();

    @Select("SELECT SUM(new_insert_count) FROM task_execution_log WHERE create_time >= CURDATE()")
    Long sumTodayNewInsertCount();
}
