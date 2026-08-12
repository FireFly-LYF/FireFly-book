package contentservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 发笔记领域事件给 search（以及以后的 feed/审核等消费者）。
 * 发送失败只打日志，不回滚主业务（最终一致；可另做补偿/对账）。
 */
@Component
public class NoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoteEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NoteEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, NoteIndexEvent event) {
        try {
            rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_CONTENT, routingKey, event);
        } catch (Exception e) {
            log.warn("发送笔记事件失败 routingKey={} noteId={}: {}",
                    routingKey, event.getId(), e.getMessage());
        }
    }
}
