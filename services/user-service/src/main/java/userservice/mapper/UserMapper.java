package userservice.mapper;

import userservice.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    /** 批量摘要（不含 password） */
    @Select("""
            <script>
            SELECT id, username, nickname, avatar_url, bio, created_at, updated_at
            FROM user
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    List<User> findByIds(@Param("ids") List<Long> ids);

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

    /** 登录时将存量 MD5 升级为 BCrypt */
    @Update("UPDATE user SET password=#{password} WHERE id=#{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);
}