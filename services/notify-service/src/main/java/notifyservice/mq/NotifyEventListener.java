package notifyservice.mq;

import notifyservice.dto.CreateNotifyRequest;
import notifyservice.service.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotifyEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotifyEventListener.class);

    private final NotifyService notifyService;

    public NotifyEventListener(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @RabbitListener(queues = MqConstants.QUEUE_NOTIFY)
    public void onNotifyEvent(NotifyEvent event) {
        if (event == null) {
            return;
        }
        CreateNotifyRequest req = new CreateNotifyRequest();
        req.setUserId(event.getUserId());
        req.setFromUserId(event.getFromUserId());
        req.setType(event.getType());
        req.setRefId(event.getRefId());
        req.setContent(event.getContent());
        try {
            notifyService.create(req);
        } catch (IllegalArgumentException e) {
            log.warn("忽略无效通知事件 type={} userId={} fromUserId={}: {}",
                    event.getType(), event.getUserId(), event.getFromUserId(), e.getMessage());
        }
    }
}
