package contentservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 实际向 Rabbit 发送笔记事件。
 * 失败必须抛出，由 OutboxRelay 记录重试；禁止再吞异常。
 */
@Component
public class NoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoteEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NoteEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, NoteIndexEvent event) {
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_CONTENT, routingKey, event);
        log.debug("已发送笔记事件 routingKey={} noteId={}", routingKey, event.getId());
    }
}
