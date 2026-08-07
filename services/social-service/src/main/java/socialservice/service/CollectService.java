package socialservice.service;

import socialservice.mapper.NoteCollectMapper;
import org.springframework.stereotype.Service;

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
}
