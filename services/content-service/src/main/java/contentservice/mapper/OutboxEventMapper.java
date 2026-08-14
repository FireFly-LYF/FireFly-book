package contentservice.mapper;

import contentservice.entity.OutboxEvent;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper {

    @Insert("INSERT INTO outbox_event(aggregate_type, aggregate_id, event_type, payload, status, attempts) " +
            "VALUES(#{aggregateType}, #{aggregateId}, #{eventType}, CAST(#{payload} AS JSON), #{status}, #{attempts})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OutboxEvent event);

    @Select("SELECT * FROM outbox_event " +
            "WHERE status = 'NEW' AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY id ASC LIMIT #{limit} " +
            "FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> selectBatchForRelay(@Param("limit") int limit);

    @Update("UPDATE outbox_event SET status='SENT', last_error=NULL, updated_at=NOW() WHERE id=#{id}")
    int markSent(@Param("id") Long id);

    @Update("UPDATE outbox_event SET attempts=#{attempts}, next_retry_at=#{nextRetryAt}, " +
            "last_error=#{lastError}, status=#{status}, updated_at=NOW() WHERE id=#{id}")
    int markRetry(@Param("id") Long id,
                  @Param("attempts") int attempts,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("lastError") String lastError,
                  @Param("status") String status);
}
