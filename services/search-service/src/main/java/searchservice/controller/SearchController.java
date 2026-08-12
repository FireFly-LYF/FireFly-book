package searchservice.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchservice.common.ApiResponse;
import searchservice.document.NoteDocument;
import searchservice.document.UserDocument;
import searchservice.dto.IndexNoteRequest;
import searchservice.dto.IndexUserRequest;
import searchservice.service.SearchIndexService;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchIndexService searchIndexService;

    public SearchController(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    /** 搜笔记标题/正文 */
    @GetMapping("/note")
    public ApiResponse<List<NoteDocument>> searchNote(
            @RequestParam("q") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            return ApiResponse.ok(searchIndexService.searchNotes(q, page, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(50001, "搜索失败: " + e.getMessage());
        }
    }

    /** 搜用户昵称/用户名 */
    @GetMapping("/user")
    public ApiResponse<List<UserDocument>> searchUser(@RequestParam("q") String q) {
        try {
            return ApiResponse.ok(searchIndexService.searchUsers(q));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(50001, "搜索失败: " + e.getMessage());
        }
    }

    /**
     * 内部接口：手动索引一篇笔记（无 MQ 时 content 也可 HTTP 调这里）。
     * 本机暂不鉴权；生产应对 /inner/** 做内网限制。
     */
    @PostMapping("/inner/index")
    public ApiResponse<Void> indexNote(@RequestBody IndexNoteRequest req) {
        try {
            NoteDocument doc = new NoteDocument(
                    req.getId(), req.getUserId(), req.getTitle(), req.getContent(), req.getCoverUrl());
            searchIndexService.indexNote(doc);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(50001, "索引失败: " + e.getMessage());
        }
    }

    /** 内部接口：删除笔记索引 */
    @DeleteMapping("/inner/note/{id}")
    public ApiResponse<Void> deleteNote(@PathVariable("id") Long id) {
        try {
            searchIndexService.deleteNote(id);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(50001, "删除索引失败: " + e.getMessage());
        }
    }

    /** 内部接口：索引/更新用户（无 MQ 时可 HTTP 调用） */
    @PostMapping("/inner/index-user")
    public ApiResponse<Void> indexUser(@RequestBody IndexUserRequest req) {
        try {
            UserDocument doc = new UserDocument(
                    req.getId(), req.getUsername(), req.getNickname(), req.getAvatarUrl());
            searchIndexService.indexUser(doc);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(50001, "索引失败: " + e.getMessage());
        }
    }
}
