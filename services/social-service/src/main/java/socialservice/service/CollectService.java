package socialservice.service;

import socialservice.cache.UserNoteIdsCache;
import socialservice.mapper.NoteCollectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollectService {

    private final NoteCollectMapper noteCollectMapper;
    private final UserNoteIdsCache userNoteIdsCache;

    public CollectService(NoteCollectMapper noteCollectMapper, UserNoteIdsCache userNoteIdsCache) {
        this.noteCollectMapper = noteCollectMapper;
        this.userNoteIdsCache = userNoteIdsCache;
    }

    public void collect(Long userId, Long noteId) {
        try {
            noteCollectMapper.insert(noteId, userId);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("已收藏");
        }
        userNoteIdsCache.evictCollected(userId);
    }

    public void uncollect(Long userId, Long noteId) {
        if (noteCollectMapper.delete(noteId, userId) == 0) {
            throw new IllegalArgumentException("尚未收藏");
        }
        userNoteIdsCache.evictCollected(userId);
    }

    public boolean collectedByMe(Long userId, Long noteId) {
        return noteCollectMapper.exists(noteId, userId) > 0;
    }

    /** 用户收藏的笔记 id（新收藏在前） */
    public List<Long> listCollectedNoteIds(Long userId, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        List<Long> cached = userNoteIdsCache.getCollected(userId, page, size);
        if (cached != null) {
            return cached;
        }
        int offset = (page - 1) * size;
        List<Long> ids = noteCollectMapper.findNoteIdsByUser(userId, offset, size);
        if (ids == null) {
            ids = List.of();
        }
        userNoteIdsCache.putCollected(userId, page, size, ids);
        return ids;
    }
}
