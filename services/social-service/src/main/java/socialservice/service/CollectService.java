package socialservice.service;

import socialservice.mapper.NoteCollectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollectService {

    private final NoteCollectMapper noteCollectMapper;

    public CollectService(NoteCollectMapper noteCollectMapper) {
        this.noteCollectMapper = noteCollectMapper;
    }

    public void collect(Long userId, Long noteId) {
        if (noteCollectMapper.exists(noteId, userId) > 0) {
            throw new IllegalArgumentException("已收藏");
        }
        noteCollectMapper.insert(noteId, userId);
    }

    public void uncollect(Long userId, Long noteId) {
        if (noteCollectMapper.delete(noteId, userId) == 0) {
            throw new IllegalArgumentException("尚未收藏");
        }
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
        int offset = (page - 1) * size;
        return noteCollectMapper.findNoteIdsByUser(userId, offset, size);
    }
}
