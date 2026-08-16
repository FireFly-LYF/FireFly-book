package userservice.controller;

import userservice.common.ApiResponse;
import userservice.entity.User;
import userservice.service.FollowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/follow/{id}")
    public ApiResponse<Void> follow(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long followeeId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            followService.follow(userId, followeeId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @DeleteMapping("/follow/{id}")
    public ApiResponse<Void> unfollow(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long followeeId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            followService.unfollow(userId, followeeId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    /** Feed 读扩散 / 回填：当前登录用户关注的人 id 列表 */
    @GetMapping("/me/following-ids")
    public ApiResponse<List<Long>> myFollowingIds(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ApiResponse.fail(40100, "未登录");
        }
        try {
            return ApiResponse.ok(followService.listFollowingIds(userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        }
    }

    /** Feed 写扩散：某用户的粉丝 id 列表 */
    @GetMapping("/{id}/follower-ids")
    public ApiResponse<List<Long>> followerIds(@PathVariable Long id) {
        try {
            return ApiResponse.ok(followService.listFollowerIds(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        }
    }

    @GetMapping("/{id}/followers")
    public ApiResponse<List<User>> followers(@PathVariable Long id) {
        try {
            return ApiResponse.ok(followService.listFollowers(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        }
    }

    @GetMapping("/{id}/following")
    public ApiResponse<List<User>> following(@PathVariable Long id) {
        try {
            return ApiResponse.ok(followService.listFollowing(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40401, e.getMessage());
        }
    }
}
