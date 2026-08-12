package searchservice.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import searchservice.document.NoteDocument;
import searchservice.document.UserDocument;
import searchservice.service.SearchIndexService;

/**
 * 消费 MQ → 写/删 ES。
 * 路由键区分动作；删笔记时 payload 通常只有 id。
 */
@Component
public class SearchEventListener {

    private static final Logger log = LoggerFactory.getLogger(SearchEventListener.class);

    private final SearchIndexService searchIndexService;

    public SearchEventListener(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @RabbitListener(queues = MqConstants.QUEUE_SEARCH_NOTE)
    public void onNoteEvent(NoteIndexEvent event,
                            @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey) {
        if (event == null || event.getId() == null) {
            log.warn("忽略无效笔记事件 routingKey={}", routingKey);
            return;
        }
        try {
            if (MqConstants.RK_NOTE_DELETED.equals(routingKey)) {
                searchIndexService.deleteNote(event.getId());
                return;
            }
            // created / updated：覆盖写（ES PUT 幂等）
            NoteDocument doc = new NoteDocument(
                    event.getId(), event.getUserId(), event.getTitle(), event.getContent(), event.getCoverUrl());
            searchIndexService.indexNote(doc);
        } catch (Exception e) {
            // 抛出可触发默认重试；这里先打日志避免毒消息拖垮消费者（教程简化）
            log.error("处理笔记索引事件失败 routingKey={} id={}: {}", routingKey, event.getId(), e.getMessage());
        }
    }

    @RabbitListener(queues = MqConstants.QUEUE_SEARCH_USER)
    public void onUserEvent(UserIndexEvent event) {
        if (event == null || event.getId() == null) {
            return;
        }
        try {
            UserDocument doc = new UserDocument(
                    event.getId(), event.getUsername(), event.getNickname(), event.getAvatarUrl());
            searchIndexService.indexUser(doc);
        } catch (Exception e) {
            log.error("处理用户索引事件失败 id={}: {}", event.getId(), e.getMessage());
        }
    }
}
