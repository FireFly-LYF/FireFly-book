package imservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ImEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ImEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public ImEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, NotifyPayload event) {
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_IM, routingKey, event);
        log.debug("已发送 IM 事件 routingKey={} refId={}", routingKey, event.getRefId());
    }
}
