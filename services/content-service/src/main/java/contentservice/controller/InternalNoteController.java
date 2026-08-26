package contentservice.controller;

import contentservice.common.ApiResponse;
import contentservice.dto.ModerationStatusRequest;
import contentservice.service.NoteService;
import org.springframework.web.bind.annotation.*;

/**
 * 内网专用：AI 服务直调（HMAC），不暴露给 Gateway 公网路由。
 */
@RestController
@RequestMapping("/api/internal/note")
public class InternalNoteController {

    private final NoteService noteService;

    public InternalNoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateModerationStatus(
            @PathVariable Long id,
            @RequestBody ModerationStatusRequest req) {
        if (req == null || req.getStatus() == null) {
            return ApiResponse.fail(40001, "status 不能为空");
        }
        try {
            noteService.applyModerationStatus(id, req.getStatus(), req.getReason());
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        }
    }
}
