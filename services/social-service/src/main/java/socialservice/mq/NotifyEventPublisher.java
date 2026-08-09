package socialservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotifyEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotifyEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public NotifyEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, NotifyEvent event) {
        try {
            rabbitTemplate.convertAndSend(MqConstants.EXCHANGE, routingKey, event);
        } catch (Exception e) {
            log.warn("发送通知事件失败 routingKey={} type={} refId={}: {}",
                    routingKey, event.getType(), event.getRefId(), e.getMessage());
        }
    }
}
