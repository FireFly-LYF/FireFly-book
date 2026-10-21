package marketservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MarketEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public MarketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, NotifyPayload event) {
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_MARKET, routingKey, event);
        log.debug("已发送市集事件 routingKey={} refId={}", routingKey, event.getRefId());
    }
}
