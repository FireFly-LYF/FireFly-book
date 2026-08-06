package contentservice.service;

import contentservice.dto.CreateNoteRequest;
import contentservice.dto.UpdateNoteRequest;
import contentservice.entity.Note;
import contentservice.mapper.NoteMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoteService {

    private final NoteMapper noteMapper;

    public NoteService(NoteMapper noteMapper) {
        this.noteMapper = noteMapper;
    }

    public Note create(Long userId, CreateNoteRequest req) {
        Note n = new Note();
        n.setUserId(userId);
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setCoverUrl(req.getCoverUrl());
        n.setStatus(1); // 已发布
        noteMapper.insert(n);
        return noteMapper.findById(n.getId());
    }

    public Note findById(Long id) {
        return noteMapper.findById(id);
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
        return noteMapper.findById(noteId);
    }

    public void delete(Long userId, Long noteId) {
        requireOwned(userId, noteId);
        noteMapper.delete(noteId);
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
