package contentservice.service;

import contentservice.dto.CreateNoteRequest;
import contentservice.dto.NoteDetailResponse;
import contentservice.dto.UpdateNoteRequest;
import contentservice.entity.Note;
import contentservice.entity.NoteMedia;
import contentservice.mapper.NoteMapper;
import contentservice.mapper.NoteMediaMapper;
import contentservice.mq.MqConstants;
import contentservice.mq.NoteEventPublisher;
import contentservice.mq.NoteIndexEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteMediaMapper noteMediaMapper;
    private final NoteEventPublisher noteEventPublisher;

    public NoteService(NoteMapper noteMapper, NoteMediaMapper noteMediaMapper, NoteEventPublisher noteEventPublisher) {
        this.noteMapper = noteMapper;
        this.noteMediaMapper = noteMediaMapper;
        this.noteEventPublisher = noteEventPublisher;
    }

    /**
     * 发笔记：事务内写 note + note_media；提交后再发 MQ，由 search 异步写 ES。
     * 不在这里 HTTP 调 search，避免拖慢发笔记、也避免 search 挂掉导致发帖失败。
     */
    @Transactional
    public NoteDetailResponse create(Long userId, CreateNoteRequest req) {
        Note n = new Note();
        n.setUserId(userId);
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setCoverUrl(req.getCoverUrl());
        n.setStatus(1); // 已发布
        noteMapper.insert(n);

        List<String> urls = req.getMediaUrls();
        if (urls != null) {
            for (int i = 0; i < urls.size(); i++) {
                NoteMedia m = new NoteMedia();
                m.setNoteId(n.getId());
                m.setMediaUrl(urls.get(i));
                m.setSortNo(i);
                noteMediaMapper.insert(m);
            }
        }

        // 必须等事务提交后再发消息，否则消费者可能读到「库里还没有」的旧状态
        publishAfterCommit(
                MqConstants.RK_NOTE_CREATED,
                NoteIndexEvent.from(n.getId(), n.getUserId(), n.getTitle(), n.getContent(), n.getCoverUrl()));

        return toDetail(n.getId());
    }

    public Note findById(Long id) {
        return noteMapper.findById(id);
    }

    /** 详情：笔记 + 图片 URL 列表 */
    public NoteDetailResponse findDetail(Long id) {
        Note n = noteMapper.findById(id);
        if (n == null) {
            return null;
        }
        return toDetail(id);
    }

    public List<Note> listByUser(Long userId, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 10;
        }
        int offset = (page - 1) * size;
        return noteMapper.listByUser(userId, size, offset);
    }

    public Note update(Long userId, Long noteId, UpdateNoteRequest req) {
        Note n = requireOwned(userId, noteId);
        if (req.getTitle() != null) {
            n.setTitle(req.getTitle());
        }
        if (req.getContent() != null) {
            n.setContent(req.getContent());
        }
        if (req.getCoverUrl() != null) {
            n.setCoverUrl(req.getCoverUrl());
        }
        noteMapper.update(n);
        Note updated = noteMapper.findById(noteId);

        // 更新后覆盖写 ES，保证搜到的是最新标题/正文
        noteEventPublisher.publish(
                MqConstants.RK_NOTE_UPDATED,
                NoteIndexEvent.from(
                        updated.getId(), updated.getUserId(),
                        updated.getTitle(), updated.getContent(), updated.getCoverUrl()));

        return updated;
    }

    @Transactional
    public void delete(Long userId, Long noteId) {
        requireOwned(userId, noteId);
        noteMediaMapper.deleteByNoteId(noteId);
        noteMapper.delete(noteId);

        // 删库提交后再删 ES，避免「库没有了还能搜到」
        publishAfterCommit(MqConstants.RK_NOTE_DELETED, NoteIndexEvent.deleted(noteId));
    }

    /**
     * 有事务则 afterCommit 再发；无事务（如 update 未标 @Transactional）则立刻发。
     */
    private void publishAfterCommit(String routingKey, NoteIndexEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    noteEventPublisher.publish(routingKey, event);
                }
            });
        } else {
            noteEventPublisher.publish(routingKey, event);
        }
    }

    private NoteDetailResponse toDetail(Long noteId) {
        NoteDetailResponse r = new NoteDetailResponse();
        r.setNote(noteMapper.findById(noteId));
        List<String> urls = noteMediaMapper.listUrlsByNoteId(noteId);
        r.setMediaUrls(urls != null ? urls : List.of());
        return r;
    }

    private Note requireOwned(Long userId, Long noteId) {
        Note n = noteMapper.findById(noteId);
        if (n == null) {
            throw new IllegalArgumentException("笔记不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new SecurityException("无权操作");
        }
        return n;
    }
}
