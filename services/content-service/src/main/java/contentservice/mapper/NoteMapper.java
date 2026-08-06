package contentservice.mapper;

import contentservice.entity.Note;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteMapper {

    @Insert("INSERT INTO note(user_id, title, content, cover_url, status) " +
            "VALUES(#{userId}, #{title}, #{content}, #{coverUrl}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

    @Select("SELECT * FROM note WHERE id=#{id}")
    Note findById(Long id);

    @Select("SELECT * FROM note WHERE user_id=#{userId} AND status=1 ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Note> listByUser(@Param("userId") Long userId,
                          @Param("limit") int limit,
                          @Param("offset") int offset);

    @Update("UPDATE note SET title=#{title}, content=#{content}, cover_url=#{coverUrl} WHERE id=#{id}")
    int update(Note note);

    @Delete("DELETE FROM note WHERE id=#{id}")
    int delete(Long id);
}
