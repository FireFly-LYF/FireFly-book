package contentservice.controller;

import contentservice.common.ApiResponse;
import contentservice.dto.BatchByIdsRequest;
import contentservice.dto.BatchLatestByUsersRequest;
import contentservice.dto.CreateNoteRequest;
import contentservice.dto.NoteDetailResponse;
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
    public ApiResponse<NoteDetailResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateNoteRequest req) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(noteService.create(userId, req, idempotencyKey));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    /** 写在 /{id} 前面，避免被当成 id */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<Note>> listByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(noteService.listByUser(userId, page, size));
    }

    /** Feed 读扩散：一次拉取多位作者最新笔记（userIds≤100） */
    @PostMapping("/users/latest")
    public ApiResponse<List<Note>> listLatestByUsers(@RequestBody BatchLatestByUsersRequest req) {
        if (req == null || req.getUserIds() == null || req.getUserIds().isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        int perUser = req.getPerUser() != null ? req.getPerUser() : 5;
        return ApiResponse.ok(noteService.listLatestByUsers(req.getUserIds(), perUser));
    }

    /** Feed 时间线水合：按 id 批量查（顺序与请求一致） */
    @PostMapping("/ids")
    public ApiResponse<List<Note>> listByIds(@RequestBody BatchByIdsRequest req) {
        if (req == null || req.getIds() == null || req.getIds().isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(noteService.listByIds(req.getIds()));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoteDetailResponse> getById(@PathVariable Long id) {
        NoteDetailResponse detail = noteService.findDetail(id);
        if (detail == null) {
            return ApiResponse.fail(40401, "笔记不存在");
        }
        return ApiResponse.ok(detail);
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
