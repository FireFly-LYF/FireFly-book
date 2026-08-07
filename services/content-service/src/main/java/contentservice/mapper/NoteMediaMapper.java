package contentservice.mapper;

import contentservice.entity.NoteMedia;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NoteMediaMapper {

    @Insert("INSERT INTO note_media(note_id, media_url, sort_no) " +
            "VALUES(#{noteId}, #{mediaUrl}, #{sortNo})")
    int insert(NoteMedia media);

    @Select("SELECT media_url FROM note_media WHERE note_id=#{noteId} ORDER BY sort_no ASC, id ASC")
    List<String> listUrlsByNoteId(Long noteId);

    @Delete("DELETE FROM note_media WHERE note_id=#{noteId}")
    int deleteByNoteId(Long noteId); // 删笔记时可顺带清
}