package userservice.mapper;

import userservice.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FollowMapper {

    @Insert("INSERT INTO follow(follower_id, followee_id) VALUES(#{followerId}, #{followeeId})")
    int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Delete("DELETE FROM follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT COUNT(1) FROM follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 粉丝：关注了该用户的人 */
    @Select("SELECT u.id, u.username, u.nickname, u.avatar_url, u.bio, u.created_at, u.updated_at " +
            "FROM user u INNER JOIN follow f ON u.id = f.follower_id " +
            "WHERE f.followee_id = #{userId} ORDER BY f.created_at DESC")
    List<User> findFollowers(@Param("userId") Long userId);

    /** 关注：该用户关注的人 */
    @Select("SELECT u.id, u.username, u.nickname, u.avatar_url, u.bio, u.created_at, u.updated_at " +
            "FROM user u INNER JOIN follow f ON u.id = f.followee_id " +
            "WHERE f.follower_id = #{userId} ORDER BY f.created_at DESC")
    List<User> findFollowing(@Param("userId") Long userId);
}
