package feedservice.mapper;

import feedservice.entity.FeedInbox;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FeedInboxMapper {

    @Insert("""
            <script>
            INSERT IGNORE INTO feed_inbox(user_id, note_id, author_id, created_at) VALUES
            <foreach collection="rows" item="r" separator=",">
              (#{r.userId}, #{r.noteId}, #{r.authorId}, #{r.createdAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<FeedInbox> rows);

    @Select("SELECT user_id, note_id, author_id, created_at FROM feed_inbox " +
            "WHERE user_id=#{userId} ORDER BY created_at DESC, note_id DESC LIMIT #{limit}")
    List<FeedInbox> listByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM feed_inbox WHERE note_id=#{noteId}")
    int deleteByNoteId(@Param("noteId") Long noteId);

    @Select("SELECT DISTINCT user_id FROM feed_inbox WHERE note_id=#{noteId}")
    List<Long> listUserIdsByNoteId(@Param("noteId") Long noteId);

    @Delete("DELETE FROM feed_inbox WHERE user_id=#{userId} AND author_id=#{authorId}")
    int deleteByUserAndAuthor(@Param("userId") Long userId, @Param("authorId") Long authorId);

    @Select("SELECT note_id FROM feed_inbox WHERE user_id=#{userId} AND author_id=#{authorId}")
    List<Long> listNoteIdsByUserAndAuthor(@Param("userId") Long userId, @Param("authorId") Long authorId);
}
