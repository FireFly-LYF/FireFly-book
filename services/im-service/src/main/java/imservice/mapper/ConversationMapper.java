package imservice.mapper;

import imservice.entity.Conversation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("INSERT INTO conversation(type, dm_key) VALUES('DM', #{dmKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Conversation c);

    @Select("SELECT * FROM conversation WHERE id=#{id}")
    Conversation findById(Long id);

    @Select("SELECT * FROM conversation WHERE dm_key=#{dmKey} LIMIT 1")
    Conversation findByDmKey(String dmKey);

    @Update("""
            UPDATE conversation
            SET last_message_id=#{lastMessageId},
                last_preview=#{lastPreview},
                last_sender_id=#{lastSenderId},
                last_message_at=NOW()
            WHERE id=#{id}
            """)
    int updateLastMessage(
            @Param("id") Long id,
            @Param("lastMessageId") Long lastMessageId,
            @Param("lastPreview") String lastPreview,
            @Param("lastSenderId") Long lastSenderId);

    @Select("""
            SELECT c.*
            FROM conversation c
            INNER JOIN conversation_member m ON m.conversation_id = c.id
            WHERE m.user_id = #{userId}
            ORDER BY COALESCE(c.last_message_at, c.created_at) DESC
            LIMIT #{limit}
            """)
    List<Conversation> listByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
