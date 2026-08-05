package userservice.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface FollowMapper {

    @Insert("INSERT INTO follow(follower_id, followee_id) VALUES(#{followerId}, #{followeeId})")
    int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Delete("DELETE FROM follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT COUNT(1) FROM follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);
}
