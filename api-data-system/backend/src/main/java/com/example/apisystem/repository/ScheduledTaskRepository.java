package com.example.apisystem.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.apisystem.entity.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface ScheduledTaskRepository extends BaseMapper<ScheduledTask> {

    @Select("SELECT * FROM scheduled_task WHERE enable_status = 'ENABLED'")
    List<ScheduledTask> findAllEnabledTasks();

    @Update("UPDATE scheduled_task SET last_execute_time = #{lastExecuteTime}, next_execute_time = #{nextExecuteTime} WHERE id = #{id}")
    void updateExecuteTime(Long id, String lastExecuteTime, String nextExecuteTime);
}
