package feedservice.mq;

import feedservice.service.FeedFanoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class FeedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FeedEventListener.class);

    private final FeedFanoutService feedFanoutService;

    public FeedEventListener(FeedFanoutService feedFanoutService) {
        this.feedFanoutService = feedFanoutService;
    }

    @RabbitListener(queues = MqConstants.QUEUE_FEED_NOTE)
    public void onNoteEvent(NoteIndexEvent event,
                            @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        if (event == null || event.getId() == null) {
            log.warn("忽略无效笔记事件 routingKey={}", routingKey);
            return;
        }
        try {
            if (MqConstants.RK_NOTE_DELETED.equals(routingKey)) {
                feedFanoutService.onNoteDeleted(event.getId());
                return;
            }
            // 先审后发：仅审核通过后的 note.published 写扩散；忽略遗留 note.created 绑定
            if (MqConstants.RK_NOTE_PUBLISHED.equals(routingKey)) {
                feedFanoutService.onNoteCreated(event);
            }
        } catch (RuntimeException e) {
            log.error("处理笔记 Feed 事件失败 routingKey={} id={}: {}", routingKey, event.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("处理笔记 Feed 事件失败 routingKey={} id={}: {}", routingKey, event.getId(), e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    @RabbitListener(queues = MqConstants.QUEUE_FEED_FOLLOW)
    public void onFollowEvent(FollowEvent event,
                              @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        if (event == null || event.getUserId() == null || event.getFromUserId() == null) {
            log.warn("忽略无效关注事件 routingKey={}", routingKey);
            return;
        }
        Long followeeId = event.getUserId();
        Long followerId = event.getFromUserId();
        try {
            if (MqConstants.RK_FOLLOW_DELETED.equals(routingKey)) {
                feedFanoutService.onFollowDeleted(followerId, followeeId);
                return;
            }
            if (MqConstants.RK_FOLLOW_CREATED.equals(routingKey)) {
                feedFanoutService.onFollowCreated(followerId, followeeId);
            }
        } catch (RuntimeException e) {
            log.error("处理关注 Feed 事件失败 routingKey={} follower={} followee={}: {}",
                    routingKey, followerId, followeeId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("处理关注 Feed 事件失败 routingKey={} follower={} followee={}: {}",
                    routingKey, followerId, followeeId, e.getMessage());
            throw new IllegalStateException(e);
        }
    }
}
