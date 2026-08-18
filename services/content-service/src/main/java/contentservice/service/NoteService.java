package contentservice.service;

import contentservice.cache.NoteCache;
import contentservice.dto.CreateNoteRequest;
import contentservice.dto.NoteDetailResponse;
import contentservice.dto.UpdateNoteRequest;
import contentservice.entity.Note;
import contentservice.entity.NoteMedia;
import contentservice.mapper.NoteMapper;
import contentservice.mapper.NoteMediaMapper;
import contentservice.mq.MqConstants;
import contentservice.mq.NoteIndexEvent;
import contentservice.mq.OutboxRelay;
import contentservice.mq.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteMediaMapper noteMediaMapper;
    private final OutboxService outboxService;
    private final OutboxRelay outboxRelay;
    private final NoteCache noteCache;

    public NoteService(
            NoteMapper noteMapper,
            NoteMediaMapper noteMediaMapper,
            OutboxService outboxService,
            OutboxRelay outboxRelay,
            NoteCache noteCache) {
        this.noteMapper = noteMapper;
        this.noteMediaMapper = noteMediaMapper;
        this.outboxService = outboxService;
        this.outboxRelay = outboxRelay;
        this.noteCache = noteCache;
    }

    /**
     * 发笔记：事务内写 note + note_media + outbox；
     * 提交后再触发一次投递（失败由定时 OutboxRelay 继续重试）。
     */
    @Transactional
    public NoteDetailResponse create(Long userId, CreateNoteRequest req) {
        Note n = new Note();
        n.setUserId(userId);
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setCoverUrl(req.getCoverUrl());
        n.setStatus(1);
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

        enqueueAndKick(
                MqConstants.RK_NOTE_CREATED,
                n.getId(),
                NoteIndexEvent.from(n.getId(), n.getUserId(), n.getTitle(), n.getContent(), n.getCoverUrl()));

        return toDetail(n);
    }

    public Note findById(Long id) {
        Note cached = noteCache.get(id);
        if (cached != null) {
            return cached;
        }
        Note n = noteMapper.findById(id);
        if (n != null) {
            noteCache.put(n);
        }
        return n;
    }

    public NoteDetailResponse findDetail(Long id) {
        Note n = findById(id);
        if (n == null) {
            return null;
        }
        return toDetail(n);
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

    /**
     * 批量拉多位作者最新笔记。限制 userIds≤100、perUser≤20，避免一次打爆库。
     */
    public List<Note> listLatestByUsers(List<Long> userIds, int perUser) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        int limitPer = perUser < 1 ? 5 : Math.min(perUser, 20);
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(
                userIds.stream().filter(Objects::nonNull).limit(100).toList()));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Note> notes = noteMapper.listLatestByUsers(ids, limitPer);
        return notes != null ? notes : List.of();
    }

    /** 按 id 批量查；返回顺序与请求 ids 对齐（缺失则跳过） */
    public List<Note> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> ordered = ids.stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if (ordered.isEmpty()) {
            return List.of();
        }
        var byId = noteCache.getMany(ordered);
        List<Long> miss = ordered.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!miss.isEmpty()) {
            List<Note> found = noteMapper.listByIds(miss);
            if (found != null && !found.isEmpty()) {
                noteCache.putMany(found);
                for (Note n : found) {
                    if (n.getId() != null) {
                        byId.put(n.getId(), n);
                    }
                }
            }
        }
        List<Note> out = new ArrayList<>();
        for (Long id : ordered) {
            Note n = byId.get(id);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    @Transactional
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
        noteCache.evict(noteId);
        Note updated = noteMapper.findById(noteId);

        enqueueAndKick(
                MqConstants.RK_NOTE_UPDATED,
                updated.getId(),
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
        noteCache.evict(noteId);

        enqueueAndKick(MqConstants.RK_NOTE_DELETED, noteId, NoteIndexEvent.deleted(noteId, userId));
    }

    /**
     * 事务内写 outbox；提交成功后再 kick Relay（此时行已可见）。
     */
    private void enqueueAndKick(String routingKey, Long noteId, NoteIndexEvent event) {
        outboxService.enqueueNoteEvent(routingKey, noteId, event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxRelay.relayBatch();
                }
            });
        } else {
            outboxRelay.relayBatch();
        }
    }

    private NoteDetailResponse toDetail(Note note) {
        NoteDetailResponse r = new NoteDetailResponse();
        r.setNote(note);
        List<String> urls = noteMediaMapper.listUrlsByNoteId(note.getId());
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
