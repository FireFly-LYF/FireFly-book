package contentservice.mapper;

import contentservice.entity.Note;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteMapper {

    @Insert("INSERT INTO note(user_id, title, content, cover_url, status, idem_key) " +
            "VALUES(#{userId}, #{title}, #{content}, #{coverUrl}, #{status}, #{idemKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

    @Select("SELECT * FROM note WHERE user_id=#{userId} AND idem_key=#{idemKey} LIMIT 1")
    Note findByUserAndIdemKey(@Param("userId") Long userId, @Param("idemKey") String idemKey);

    @Select("SELECT * FROM note WHERE id=#{id}")
    Note findById(Long id);

    @Select("SELECT * FROM note WHERE user_id=#{userId} AND status=1 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Note> listByUser(@Param("userId") Long userId,
                          @Param("limit") int limit,
                          @Param("offset") int offset);

    /**
     * 每位作者取最新 perUser 条（MySQL 8 窗口函数），供 Feed 一次批量拉取。
     */
    @Select("""
            <script>
            SELECT id, user_id, title, content, cover_url, status, like_count, created_at, updated_at
            FROM (
              SELECT n.id, n.user_id, n.title, n.content, n.cover_url, n.status, n.like_count,
                     n.created_at, n.updated_at,
                     ROW_NUMBER() OVER (PARTITION BY n.user_id ORDER BY n.id DESC) AS rn
              FROM note n
              WHERE n.status = 1
                AND n.user_id IN
                <foreach collection="userIds" item="uid" open="(" separator="," close=")">
                  #{uid}
                </foreach>
            ) t
            WHERE t.rn &lt;= #{perUser}
            ORDER BY t.id DESC
            </script>
            """)
    List<Note> listLatestByUsers(@Param("userIds") List<Long> userIds,
                                 @Param("perUser") int perUser);

    @Select("""
            <script>
            SELECT * FROM note
            WHERE status = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<Note> listByIds(@Param("ids") List<Long> ids);

    @Update("UPDATE note SET title=#{title}, content=#{content}, cover_url=#{coverUrl} WHERE id=#{id}")
    int update(Note note);

    @Update("UPDATE note SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    @Delete("DELETE FROM note WHERE id=#{id}")
    int delete(Long id);
}
