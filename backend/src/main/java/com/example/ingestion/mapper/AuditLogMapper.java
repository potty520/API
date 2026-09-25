package com.example.ingestion.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ingestion.entity.AuditLog;
import org.apache.ibatis.annotations.Select;

public interface AuditLogMapper extends BaseMapper<AuditLog> {
    /** 锁住链尾, 让多实例部署下的审计写入串行化, 避免读到同一个 prev_hash 造成分叉。 */
    @Select("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1 FOR UPDATE")
    AuditLog selectLastForUpdate();
}
