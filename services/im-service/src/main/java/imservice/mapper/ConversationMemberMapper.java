package imservice.mapper;

import imservice.entity.ConversationMember;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ConversationMemberMapper {

    @Insert("INSERT INTO conversation_member(conversation_id, user_id, unread_count, last_read_message_id) " +
            "VALUES(#{conversationId}, #{userId}, 0, 0)")
    int insert(ConversationMember m);

    @Select("SELECT * FROM conversation_member WHERE conversation_id=#{conversationId} AND user_id=#{userId}")
    ConversationMember find(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Select("SELECT * FROM conversation_member WHERE conversation_id=#{conversationId}")
    List<ConversationMember> listByConversation(Long conversationId);

    @Update("UPDATE conversation_member SET unread_count = unread_count + 1 " +
            "WHERE conversation_id=#{conversationId} AND user_id=#{userId}")
    int incrUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Update("""
            UPDATE conversation_member
            SET unread_count = 0,
                last_read_message_id = GREATEST(last_read_message_id, #{messageId})
            WHERE conversation_id=#{conversationId} AND user_id=#{userId}
            """)
    int markRead(
            @Param("conversationId") Long conversationId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId);

    @Select("SELECT COALESCE(SUM(unread_count), 0) FROM conversation_member WHERE user_id=#{userId}")
    int sumUnread(Long userId);

    @Select("""
            SELECT user_id FROM conversation_member
            WHERE conversation_id=#{conversationId} AND user_id <> #{excludeUserId}
            LIMIT 1
            """)
    Long findPeerUserId(@Param("conversationId") Long conversationId, @Param("excludeUserId") Long excludeUserId);
}
