package contentservice.service;

import contentservice.dto.CreateNoteRequest;
import contentservice.dto.NoteDetailResponse;
import contentservice.dto.UpdateNoteRequest;
import contentservice.entity.Note;
import contentservice.entity.NoteMedia;
import contentservice.mapper.NoteMapper;
import contentservice.mapper.NoteMediaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoteService {

    private final NoteMapper noteMapper;
    private final NoteMediaMapper noteMediaMapper;

    public NoteService(NoteMapper noteMapper, NoteMediaMapper noteMediaMapper) {
        this.noteMapper = noteMapper;
        this.noteMediaMapper = noteMediaMapper;
    }

    /** 发笔记：事务内写 note + note_media */
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
        return noteMapper.findById(noteId);
    }

    @Transactional
    public void delete(Long userId, Long noteId) {
        requireOwned(userId, noteId);
        noteMediaMapper.deleteByNoteId(noteId);
        noteMapper.delete(noteId);
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
