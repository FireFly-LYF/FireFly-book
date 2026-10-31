package imservice.mq;

import imservice.entity.OutboxEvent;
import imservice.mapper.OutboxEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService {
    public static final String AGGREGATE_MESSAGE = "im_message";

    private final OutboxEventMapper outboxEventMapper;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public OutboxService(OutboxEventMapper outboxEventMapper) {
        this.outboxEventMapper = outboxEventMapper;
    }

    @Transactional
    public void enqueue(String routingKey, Long messageId, NotifyPayload event) {
        if (routingKey == null || messageId == null || event == null) {
            throw new IllegalArgumentException("outbox 参数不完整");
        }
        OutboxEvent row = new OutboxEvent();
        row.setAggregateType(AGGREGATE_MESSAGE);
        row.setAggregateId(messageId);
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
