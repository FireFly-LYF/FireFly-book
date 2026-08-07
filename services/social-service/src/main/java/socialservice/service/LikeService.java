package socialservice.service;

import socialservice.mapper.NoteLikeMapper;
import org.springframework.stereotype.Service;

@Service
public class LikeService {

    private final NoteLikeMapper noteLikeMapper;

    public LikeService(NoteLikeMapper noteLikeMapper) {
        this.noteLikeMapper = noteLikeMapper;
    }

    public void like(Long userId, Long noteId) {
        if (noteLikeMapper.exists(noteId, userId) > 0) {
            throw new IllegalArgumentException("已点过赞");
        }
        noteLikeMapper.insert(noteId, userId);
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
}
