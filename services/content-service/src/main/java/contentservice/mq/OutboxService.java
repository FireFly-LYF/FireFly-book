package contentservice.mq;

import contentservice.entity.OutboxEvent;
import contentservice.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 在业务事务内写入 outbox，保证「库成功 ⇒ 必有待投递事件」。 */
@Service
public class OutboxService {

    public static final String AGGREGATE_NOTE = "note";

    private final OutboxEventMapper outboxEventMapper;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OutboxService(OutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    @Transactional
    public void enqueueNoteEvent(String routingKey, Long noteId, NoteIndexEvent event) {
        if (routingKey == null || noteId == null || event == null) {
            throw new IllegalArgumentException("outbox 参数不完整");
        }
        OutboxEvent row = new OutboxEvent();
        row.setAggregateType(AGGREGATE_NOTE);
        row.setAggregateId(noteId);
        row.setEventType(routingKey);
        try {
            row.setPayload(jsonMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("序列化 outbox payload 失败", e);
        }
        row.setStatus(OutboxEvent.STATUS_NEW);
        row.setAttempts(0);
        outboxEventMapper.insert(row);
    }
}
