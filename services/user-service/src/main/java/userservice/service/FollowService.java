package userservice.service;

import userservice.cache.FollowListCache;
import userservice.entity.User;
import userservice.mapper.FollowMapper;
import userservice.mq.MqConstants;
import userservice.mq.NotifyEvent;
import userservice.mq.NotifyEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final UserService userService;
    private final FollowListCache followListCache;
    private final NotifyEventPublisher notifyEventPublisher;

    public FollowService(
            FollowMapper followMapper,
            UserService userService,
            FollowListCache followListCache,
            NotifyEventPublisher notifyEventPublisher) {
        this.followMapper = followMapper;
        this.userService = userService;
        this.followListCache = followListCache;
        this.notifyEventPublisher = notifyEventPublisher;
    }

    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        if (userService.findById(followeeId) == null) {
            throw new IllegalArgumentException("被关注用户不存在");
        }
        if (followMapper.exists(followerId, followeeId) > 0) {
            throw new IllegalArgumentException("已关注该用户");
        }
        followMapper.insert(followerId, followeeId);
        followListCache.evictFollowing(followerId);
        followListCache.evictFollowers(followeeId);
        NotifyEvent event = new NotifyEvent(
                followeeId, followerId, "FOLLOW", null, "关注了你");
        notifyEventPublisher.publish(MqConstants.RK_FOLLOW_CREATED, event);
    }

    public void unfollow(Long followerId, Long followeeId) {
        if (followMapper.delete(followerId, followeeId) == 0) {
            throw new IllegalArgumentException("未关注该用户");
        }
        followListCache.evictFollowing(followerId);
        followListCache.evictFollowers(followeeId);
        // userId=被取关者, fromUserId=取关者（与 follow 事件字段对齐，供 Feed 清 inbox）
        NotifyEvent event = new NotifyEvent(
                followeeId, followerId, "UNFOLLOW", null, "取消关注");
        notifyEventPublisher.publish(MqConstants.RK_FOLLOW_DELETED, event);
    }

    public List<User> listFollowers(Long userId) {
        return hydrate(listFollowerIds(userId));
    }

    public List<User> listFollowing(Long userId) {
        return hydrate(listFollowingIds(userId));
    }

    public List<Long> listFollowingIds(Long userId) {
        requireUser(userId);
        List<Long> cached = followListCache.getFollowingIds(userId);
        if (cached != null) {
            return cached;
        }
        List<Long> ids = followMapper.findFollowingIds(userId);
        if (ids == null) {
            ids = List.of();
        }
        followListCache.putFollowingIds(userId, ids);
        return ids;
    }

    public List<Long> listFollowerIds(Long userId) {
        requireUser(userId);
        List<Long> cached = followListCache.getFollowerIds(userId);
        if (cached != null) {
            return cached;
        }
        List<Long> ids = followMapper.findFollowerIds(userId);
        if (ids == null) {
            ids = List.of();
        }
        followListCache.putFollowerIds(userId, ids);
        return ids;
    }

    private List<User> hydrate(List<Long> ids) {
        List<User> out = new ArrayList<>();
        for (Long id : ids) {
            User u = userService.findById(id);
            if (u != null) {
                out.add(u);
            }
        }
        return out;
    }

    private void requireUser(Long userId) {
        if (userService.findById(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
    }
}
