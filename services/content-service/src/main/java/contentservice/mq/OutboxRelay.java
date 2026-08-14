package contentservice.mq;

import contentservice.entity.OutboxEvent;
import contentservice.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫描 outbox，把 NEW 事件投递到 Rabbit；失败则退避重试，超过上限标 DEAD。
 * 使用 TransactionTemplate，避免同类自调用导致 @Transactional 不生效。
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventMapper outboxEventMapper;
    private final NoteEventPublisher noteEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Value("${firefly.outbox.batch-size:50}")
    private int batchSize;

    @Value("${firefly.outbox.max-attempts:20}")
    private int maxAttempts;

    public OutboxRelay(
            OutboxEventMapper outboxEventMapper,
            NoteEventPublisher noteEventPublisher,
            TransactionTemplate transactionTemplate) {
        this.outboxEventMapper = outboxEventMapper;
        this.noteEventPublisher = noteEventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${firefly.outbox.poll-interval-ms:2000}")
    public void poll() {
        relayBatch();
    }

    /** 事务提交后可立刻调用，降低搜索可见延迟。 */
    public void relayBatch() {
        try {
            transactionTemplate.executeWithoutResult(status -> doRelayInTx());
        } catch (Exception e) {
            log.warn("outbox 投递轮次失败: {}", e.getMessage());
        }
    }

    private void doRelayInTx() {
        List<OutboxEvent> batch = outboxEventMapper.selectBatchForRelay(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent row : batch) {
            try {
                NoteIndexEvent event = jsonMapper.readValue(row.getPayload(), NoteIndexEvent.class);
                noteEventPublisher.publish(row.getEventType(), event);
                outboxEventMapper.markSent(row.getId());
                log.debug("outbox 已投递 id={} type={} noteId={}",
                        row.getId(), row.getEventType(), row.getAggregateId());
            } catch (Exception e) {
                int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
                String err = trimError(e.getMessage());
                if (attempts >= maxAttempts) {
                    outboxEventMapper.markRetry(row.getId(), attempts, null, err, OutboxEvent.STATUS_DEAD);
                    log.error("outbox 投递放弃 id={} type={} attempts={}: {}",
                            row.getId(), row.getEventType(), attempts, err);
                } else {
                    LocalDateTime next = LocalDateTime.now().plusSeconds(backoffSeconds(attempts));
                    outboxEventMapper.markRetry(row.getId(), attempts, next, err, OutboxEvent.STATUS_NEW);
                    log.warn("outbox 投递失败将重试 id={} attempts={} next={}: {}",
                            row.getId(), attempts, next, err);
                }
            }
        }
    }

    private static long backoffSeconds(int attempts) {
        long sec = 1L << Math.min(attempts, 8);
        return Math.min(sec, 300L);
    }

    private static String trimError(String msg) {
        if (msg == null) {
            return "unknown";
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }
}
