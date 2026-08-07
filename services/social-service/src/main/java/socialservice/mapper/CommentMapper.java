package socialservice.mapper;

import socialservice.entity.Comment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("INSERT INTO comment(note_id, user_id, parent_id, content) " +
            "VALUES(#{noteId}, #{userId}, #{parentId}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT * FROM comment WHERE id=#{id}")
    Comment findById(Long id);

    @Select("SELECT * FROM comment WHERE note_id=#{noteId} ORDER BY id ASC")
    List<Comment> listByNoteId(@Param("noteId") Long noteId);
}
