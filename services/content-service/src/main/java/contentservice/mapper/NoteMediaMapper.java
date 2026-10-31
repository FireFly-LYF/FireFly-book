package contentservice.mapper;

import contentservice.entity.NoteMedia;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface NoteMediaMapper {

    @Insert("INSERT INTO note_media(note_id, media_url, sort_no) " +
            "VALUES(#{noteId}, #{mediaUrl}, #{sortNo})")
    int insert(NoteMedia media);

    @Select("SELECT media_url FROM note_media WHERE note_id=#{noteId} ORDER BY sort_no ASC, id ASC")
    List<String> listUrlsByNoteId(Long noteId);

    @Select("SELECT COUNT(1) FROM note_media WHERE note_id=#{noteId}")
    int countByNoteId(Long noteId);

    @Select("""
            <script>
            SELECT note_id AS id, COUNT(1) AS c
            FROM note_media
            WHERE note_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            GROUP BY note_id
            </script>
            """)
    List<Map<String, Object>> countGrouped(@Param("ids") List<Long> ids);

    @Delete("DELETE FROM note_media WHERE note_id=#{noteId}")
    int deleteByNoteId(Long noteId); // 删笔记时可顺带清
}
