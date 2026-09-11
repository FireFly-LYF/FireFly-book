package contentservice.service;

import contentservice.cache.NoteCache;
import contentservice.cache.NoteDetailResponseCache;
import contentservice.cache.NoteListCache;
import contentservice.common.ApiResponse;
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
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteMediaMapper noteMediaMapper;
    private final OutboxService outboxService;
    private final OutboxRelay outboxRelay;
    private final NoteCache noteCache;
    private final NoteListCache noteListCache;
    private final NoteDetailResponseCache noteDetailResponseCache;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public NoteService(
            NoteMapper noteMapper,
            NoteMediaMapper noteMediaMapper,
            OutboxService outboxService,
            OutboxRelay outboxRelay,
            NoteCache noteCache,
            NoteListCache noteListCache,
            NoteDetailResponseCache noteDetailResponseCache) {
        this.noteMapper = noteMapper;
        this.noteMediaMapper = noteMediaMapper;
        this.outboxService = outboxService;
        this.outboxRelay = outboxRelay;
        this.noteCache = noteCache;
        this.noteListCache = noteListCache;
        this.noteDetailResponseCache = noteDetailResponseCache;
    }

    @PostConstruct
    void bindDetailCache() {
        noteDetailResponseCache.bindDeepLoader(this::loadDetailResponseBytes);
    }

    /**
     * 发笔记（先审后发）：事务内写 note(status=审核中) + note_media + outbox(note.created)；
     * 审核通过后再发 note.published。提交后 kick OutboxRelay。
     * 带 Idempotency-Key 时 (user_id, idem_key) 唯一，重试返回第一次的笔记。
     */
    @Transactional
    public NoteDetailResponse create(Long userId, CreateNoteRequest req, String idempotencyKey) {
        String idemKey = normalizeIdempotencyKey(idempotencyKey);
        if (idemKey != null) {
            Note existed = noteMapper.findByUserAndIdemKey(userId, idemKey);
            if (existed != null) {
                return toDetail(existed);
            }
        }

        Note n = new Note();
        n.setUserId(userId);
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setCoverUrl(req.getCoverUrl());
        // 先审后发：入库为审核中，通过后才发 note.published 进 Feed/ES
        n.setStatus(Note.STATUS_PENDING);
        n.setIdemKey(idemKey);
        try {
            noteMapper.insert(n);
        } catch (DuplicateKeyException e) {
            Note existed = noteMapper.findByUserAndIdemKey(userId, idemKey);
            if (existed != null) {
                return toDetail(existed);
            }
            throw e;
        }

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

        noteCache.put(n);
        if (urls != null) {
            noteCache.putMedia(n.getId(), urls);
        } else {
            noteCache.putMedia(n.getId(), List.of());
        }
        noteListCache.evictUser(userId);
        List<String> mediaUrls = urls != null ? List.copyOf(urls) : List.of();
        enqueueAndKick(
                MqConstants.RK_NOTE_CREATED,
                n.getId(),
                NoteIndexEvent.from(
                        n.getId(), n.getUserId(), n.getTitle(), n.getContent(),
                        n.getCoverUrl(), mediaUrls));

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

    public NoteDetailResponse findDetail(Long id, Long viewerUserId) {
        Note n = findById(id);
        if (n == null) {
            return null;
        }
        if (!canViewNote(n, viewerUserId)) {
            return null;
        }
        return toDetail(n);
    }

    /** 整包 ApiResponse JSON，供详情接口直接写出。 */
    public byte[] detailResponseJson(Long id, Long viewerUserId) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        Note n = findById(id);
        if (n == null) {
            return failJson(40401, "笔记不存在");
        }
        if (!canViewNote(n, viewerUserId)) {
            return failJson(40401, "笔记不存在");
        }
        // 未发布内容因可见性依赖 viewer，不走公共详情缓存
        if (!n.isPublished()) {
            return okDetailJson(toDetail(n));
        }
        return noteDetailResponseCache.get(id);
    }

    private byte[] loadDetailResponseBytes(Long id) {
        try {
            NoteDetailResponse detail = findDetail(id, null);
            if (detail == null) {
                return jsonMapper.writeValueAsBytes(ApiResponse.fail(40401, "笔记不存在"));
            }
            return jsonMapper.writeValueAsBytes(ApiResponse.ok(detail));
        } catch (Exception e) {
            throw new IllegalStateException("serialize note detail failed", e);
        }
    }

    private static boolean canViewNote(Note n, Long viewerUserId) {
        if (n.isPublished()) {
            return true;
        }
        return viewerUserId != null && viewerUserId.equals(n.getUserId());
    }

    private byte[] failJson(int code, String message) {
        try {
            return jsonMapper.writeValueAsBytes(ApiResponse.fail(code, message));
        } catch (Exception e) {
            throw new IllegalStateException("serialize fail response", e);
        }
    }

    private byte[] okDetailJson(NoteDetailResponse detail) {
        try {
            return jsonMapper.writeValueAsBytes(ApiResponse.ok(detail));
        } catch (Exception e) {
            throw new IllegalStateException("serialize note detail failed", e);
        }
    }

    public List<Note> listByUser(Long userId, int page, int size, Long viewerUserId) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1) {
            size = 10;
        }
        boolean ownerView = viewerUserId != null && viewerUserId.equals(userId);
        // 本人主页含待审；外人只看已发布（缓存仅服务公开墙）
        if (!ownerView) {
            List<Note> cached = noteListCache.getWall(userId, page, size);
            if (cached != null) {
                return cached;
            }
        }
        int offset = (page - 1) * size;
        List<Note> notes = ownerView
                ? noteMapper.listByOwner(userId, size, offset)
                : noteMapper.listByUser(userId, size, offset);
        if (notes == null) {
            notes = List.of();
        }
        if (!ownerView) {
            noteListCache.putWall(userId, page, size, notes);
        }
        noteCache.putMany(notes);
        return notes;
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
        List<Note> out = new ArrayList<>();
        List<Long> miss = new ArrayList<>();
        for (Long uid : ids) {
            List<Note> cached = noteListCache.getLatest(uid, limitPer);
            if (cached != null) {
                out.addAll(cached);
            } else {
                miss.add(uid);
            }
        }
        if (!miss.isEmpty()) {
            List<Note> fromDb = noteMapper.listLatestByUsers(miss, limitPer);
            Map<Long, List<Note>> grouped = new LinkedHashMap<>();
            for (Long uid : miss) {
                grouped.put(uid, new ArrayList<>());
            }
            if (fromDb != null) {
                for (Note n : fromDb) {
                    if (n.getUserId() != null) {
                        grouped.computeIfAbsent(n.getUserId(), k -> new ArrayList<>()).add(n);
                    }
                }
                noteCache.putMany(fromDb);
            }
            for (Long uid : miss) {
                List<Note> part = grouped.getOrDefault(uid, List.of());
                noteListCache.putLatest(uid, limitPer, part);
                out.addAll(part);
            }
        }
        out.sort(Comparator.comparing(
                (Note note) -> note.getId(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed());
        return out;
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
            if (n != null && n.isPublished()) {
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
        noteDetailResponseCache.evict(noteId);
        noteListCache.evictUser(userId);
        Note updated = findById(noteId);

        // 仅已发布笔记同步搜索索引；待审/拒绝不写 ES
        if (updated != null && updated.isPublished()) {
            enqueueAndKick(
                    MqConstants.RK_NOTE_UPDATED,
                    updated.getId(),
                    NoteIndexEvent.from(
                            updated.getId(), updated.getUserId(),
                            updated.getTitle(), updated.getContent(), updated.getCoverUrl(),
                            listMediaUrls(noteId)));
        }

        return updated;
    }

    /**
     * AI 审核回写（先审后发）：
     * 待审(2)→通过(1) 发 note.published 进 Feed/ES；
     * 待审(2)→拒绝(3) 仅改状态，不发下架事件。
     */
    @Transactional
    public void applyModerationStatus(Long noteId, int status, String reason) {
        if (status != Note.STATUS_PUBLISHED && status != Note.STATUS_REJECTED) {
            throw new IllegalArgumentException("status 仅支持 1(通过) 或 3(拒绝)");
        }
        Note n = findById(noteId);
        if (n == null) {
            throw new IllegalArgumentException("笔记不存在");
        }
        Integer prev = n.getStatus();
        if (prev != null && prev == status) {
            return;
        }
        if (prev == null || prev != Note.STATUS_PENDING) {
            throw new IllegalArgumentException("仅审核中的笔记可回写状态");
        }
        noteMapper.updateStatus(noteId, status);
        noteCache.evict(noteId);
        noteDetailResponseCache.evict(noteId);
        noteListCache.evictUser(n.getUserId());
        if (status == Note.STATUS_PUBLISHED) {
            Note published = findById(noteId);
            if (published == null) {
                return;
            }
            enqueueAndKick(
                    MqConstants.RK_NOTE_PUBLISHED,
                    noteId,
                    NoteIndexEvent.from(
                            published.getId(),
                            published.getUserId(),
                            published.getTitle(),
                            published.getContent(),
                            published.getCoverUrl(),
                            listMediaUrls(noteId)));
        }
    }

    @Transactional
    public void delete(Long userId, Long noteId) {
        requireOwned(userId, noteId);
        noteMediaMapper.deleteByNoteId(noteId);
        noteMapper.delete(noteId);
        noteCache.evict(noteId);
        noteDetailResponseCache.evict(noteId);
        noteListCache.evictUser(userId);

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
        List<String> urls = listMediaUrls(note.getId());
        r.setMediaUrls(urls);
        return r;
    }

    private List<String> listMediaUrls(Long noteId) {
        List<String> urls = noteCache.getMedia(noteId);
        if (urls == null) {
            urls = noteMediaMapper.listUrlsByNoteId(noteId);
            if (urls == null) {
                urls = List.of();
            }
            noteCache.putMedia(noteId, urls);
        }
        return urls;
    }

    private Note requireOwned(Long userId, Long noteId) {
        Note n = findById(noteId);
        if (n == null) {
            throw new IllegalArgumentException("笔记不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new SecurityException("无权操作");
        }
        return n;
    }

    static String normalizeIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key 最长 64 字符");
        }
        return key;
    }
}
