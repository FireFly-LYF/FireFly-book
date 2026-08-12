package userservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 注册/改资料后通知 search 更新用户搜索副本 */
@Component
public class UserIndexEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserIndexEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserIndexEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUpserted(UserIndexEvent event) {
        try {
            rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_USER, MqConstants.RK_USER_UPSERTED, event);
        } catch (Exception e) {
            log.warn("发送用户索引事件失败 userId={}: {}", event.getId(), e.getMessage());
        }
    }
}
