package userservice.mapper;

import userservice.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    /** 登录 / 注册查重 */
    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("INSERT INTO user(username, password, nickname, avatar_url) " +
            "VALUES(#{username}, #{password}, #{nickname}, #{avatarUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 改资料：由 Service 先合并非空字段再整行更新 */
    @Update("UPDATE user SET nickname=#{nickname}, avatar_url=#{avatarUrl}, bio=#{bio} WHERE id=#{id}")
    int updateProfile(User user);
}