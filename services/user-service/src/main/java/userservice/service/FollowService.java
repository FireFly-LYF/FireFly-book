package userservice.service;

import userservice.mapper.FollowMapper;
import userservice.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    public FollowService(FollowMapper followMapper, UserMapper userMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
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
    }

    public void unfollow(Long followerId, Long followeeId) {
        if (followMapper.delete(followerId, followeeId) == 0) {
            throw new IllegalArgumentException("未关注该用户");
        }
    }
}
