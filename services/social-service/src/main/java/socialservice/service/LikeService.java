package socialservice.service;

import socialservice.client.NoteAuthorClient;
import socialservice.mapper.NoteLikeMapper;
import socialservice.mq.MqConstants;
import socialservice.mq.NotifyEvent;
import socialservice.mq.NotifyEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final NoteLikeMapper noteLikeMapper;
    private final NoteAuthorClient noteAuthorClient;
    private final NotifyEventPublisher notifyEventPublisher;

    public LikeService(
            NoteLikeMapper noteLikeMapper,
            NoteAuthorClient noteAuthorClient,
            NotifyEventPublisher notifyEventPublisher) {
        this.noteLikeMapper = noteLikeMapper;
        this.noteAuthorClient = noteAuthorClient;
        this.notifyEventPublisher = notifyEventPublisher;
    }

    public void like(Long userId, Long noteId) {
        if (noteLikeMapper.exists(noteId, userId) > 0) {
            throw new IllegalArgumentException("已点过赞");
        }
        noteLikeMapper.insert(noteId, userId);
        publishLikeNotify(userId, noteId);
    }

    public void unlike(Long userId, Long noteId) {
        if (noteLikeMapper.delete(noteId, userId) == 0) {
            throw new IllegalArgumentException("尚未点赞");
        }
    }

    public long count(Long noteId) {
        return noteLikeMapper.countByNoteId(noteId);
    }

    public boolean likedByMe(Long userId, Long noteId) {
        return noteLikeMapper.exists(noteId, userId) > 0;
    }

    private void publishLikeNotify(Long fromUserId, Long noteId) {
        Long authorId = noteAuthorClient.findAuthorId(noteId);
        if (authorId == null) {
            log.warn("无法获取笔记作者，跳过点赞通知 noteId={}", noteId);
            return;
        }
        if (authorId.equals(fromUserId)) {
            return;
        }
        NotifyEvent event = new NotifyEvent(
                authorId, fromUserId, "LIKE", noteId, "赞了你的笔记");
        notifyEventPublisher.publish(MqConstants.RK_LIKE_CREATED, event);
    }
}
