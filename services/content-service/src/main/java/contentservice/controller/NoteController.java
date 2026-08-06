package contentservice.controller;

import contentservice.common.ApiResponse;
import contentservice.dto.CreateNoteRequest;
import contentservice.dto.UpdateNoteRequest;
import contentservice.entity.Note;
import contentservice.service.NoteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/note")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public ApiResponse<Note> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody CreateNoteRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        return ApiResponse.ok(noteService.create(userId, req));
    }

    /** 写在 /{id} 前面，避免被当成 id */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<Note>> listByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(noteService.listByUser(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Note> getById(@PathVariable Long id) {
        Note n = noteService.findById(id);
        if (n == null) {
            return ApiResponse.fail(40401, "笔记不存在");
        }
        return ApiResponse.ok(n);
    }

    @PutMapping("/{id}")
    public ApiResponse<Note> update(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody UpdateNoteRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(noteService.update(userId, id, req));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        } catch (SecurityException e) {
            return ApiResponse.fail(40301, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            noteService.delete(userId, id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        } catch (SecurityException e) {
            return ApiResponse.fail(40301, e.getMessage());
        }
    }
}
