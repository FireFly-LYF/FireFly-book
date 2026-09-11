package socialservice.mapper;

import socialservice.entity.Comment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("INSERT INTO comment(note_id, user_id, parent_id, reply_to_user_id, content, idem_key) " +
            "VALUES(#{noteId}, #{userId}, #{parentId}, #{replyToUserId}, #{content}, #{idemKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT * FROM comment WHERE user_id=#{userId} AND idem_key=#{idemKey} LIMIT 1")
    Comment findByUserAndIdemKey(@Param("userId") Long userId, @Param("idemKey") String idemKey);

    @Select("SELECT * FROM comment WHERE id=#{id}")
    Comment findById(Long id);

    @Select("SELECT id, note_id, user_id, parent_id, reply_to_user_id, content, created_at FROM comment " +
            "WHERE note_id=#{noteId} ORDER BY id ASC LIMIT #{limit} OFFSET #{offset}")
    List<Comment> listByNoteId(
            @Param("noteId") Long noteId,
            @Param("limit") int limit,
            @Param("offset") int offset);
}
