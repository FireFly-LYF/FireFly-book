package socialservice.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface NoteLikeMapper {

    @Insert("INSERT INTO note_like(note_id, user_id) VALUES(#{noteId}, #{userId})")
    int insert(@Param("noteId") Long noteId, @Param("userId") Long userId);

    @Delete("DELETE FROM note_like WHERE note_id=#{noteId} AND user_id=#{userId}")
    int delete(@Param("noteId") Long noteId, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM note_like WHERE note_id=#{noteId} AND user_id=#{userId}")
    int exists(@Param("noteId") Long noteId, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM note_like WHERE note_id=#{noteId}")
    long countByNoteId(@Param("noteId") Long noteId);
}
