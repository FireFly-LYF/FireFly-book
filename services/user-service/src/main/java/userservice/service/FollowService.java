package userservice.service;

import userservice.entity.User;
import userservice.mapper.FollowMapper;
import userservice.mapper.UserMapper;
import userservice.mq.MqConstants;
import userservice.mq.NotifyEvent;
import userservice.mq.NotifyEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NotifyEventPublisher notifyEventPublisher;

    public FollowService(
            FollowMapper followMapper,
            UserMapper userMapper,
            NotifyEventPublisher notifyEventPublisher) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.notifyEventPublisher = notifyEventPublisher;
    }

    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        if (userMapper.findById(followeeId) == null) {
            throw new IllegalArgumentException("被关注用户不存在");
        }
        if (followMapper.exists(followerId, followeeId) > 0) {
            throw new IllegalArgumentException("已关注该用户");
        }
        followMapper.insert(followerId, followeeId);
        NotifyEvent event = new NotifyEvent(
                followeeId, followerId, "FOLLOW", null, "关注了你");
        notifyEventPublisher.publish(MqConstants.RK_FOLLOW_CREATED, event);
    }

    public void unfollow(Long followerId, Long followeeId) {
        if (followMapper.delete(followerId, followeeId) == 0) {
            throw new IllegalArgumentException("未关注该用户");
        }
        // userId=被取关者, fromUserId=取关者（与 follow 事件字段对齐，供 Feed 清 inbox）
        NotifyEvent event = new NotifyEvent(
                followeeId, followerId, "UNFOLLOW", null, "取消关注");
        notifyEventPublisher.publish(MqConstants.RK_FOLLOW_DELETED, event);
    }

    public List<User> listFollowers(Long userId) {
        requireUser(userId);
        return followMapper.findFollowers(userId);
    }

    public List<User> listFollowing(Long userId) {
        requireUser(userId);
        return followMapper.findFollowing(userId);
    }

    public List<Long> listFollowingIds(Long userId) {
        requireUser(userId);
        List<Long> ids = followMapper.findFollowingIds(userId);
        return ids != null ? ids : List.of();
    }

    public List<Long> listFollowerIds(Long userId) {
        requireUser(userId);
        List<Long> ids = followMapper.findFollowerIds(userId);
        return ids != null ? ids : List.of();
    }

    private void requireUser(Long userId) {
        if (userMapper.findById(userId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
    }
}
