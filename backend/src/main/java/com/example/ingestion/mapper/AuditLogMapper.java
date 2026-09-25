package com.example.ingestion.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ingestion.entity.AuditLog;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AuditLogMapper extends BaseMapper<AuditLog> {
    /** 锁住链尾, 让多实例部署下的审计写入串行化, 避免读到同一个 prev_hash 造成分叉。 */
    @Select("SELECT * FROM audit_log ORDER BY id DESC LIMIT 1 FOR UPDATE")
    AuditLog selectLastForUpdate();

    /** 按 id 升序取整条链, 供校验/重建使用(显式 SQL, 不依赖 MyBatis-Plus 的 lambda 缓存)。 */
    @Select("SELECT * FROM audit_log ORDER BY id ASC")
    List<AuditLog> selectAllOrdered();
}
