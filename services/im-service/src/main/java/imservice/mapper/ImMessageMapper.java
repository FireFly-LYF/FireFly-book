package imservice.mapper;

import imservice.entity.ImMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ImMessageMapper {

    @Insert("INSERT INTO im_message(conversation_id, sender_id, content, idem_key) " +
            "VALUES(#{conversationId}, #{senderId}, #{content}, #{idemKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ImMessage msg);

    @Select("SELECT * FROM im_message WHERE conversation_id=#{conversationId} AND idem_key=#{idemKey} LIMIT 1")
    ImMessage findByIdem(@Param("conversationId") Long conversationId, @Param("idemKey") String idemKey);

    @Select("""
            <script>
            SELECT * FROM im_message
            WHERE conversation_id = #{conversationId}
            <if test="afterId != null and afterId &gt; 0">
              AND id &gt; #{afterId}
            </if>
            ORDER BY id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ImMessage> listAfter(
            @Param("conversationId") Long conversationId,
            @Param("afterId") Long afterId,
            @Param("limit") int limit);

    @Select("""
            SELECT * FROM (
              SELECT * FROM im_message
              WHERE conversation_id = #{conversationId}
              ORDER BY id DESC
              LIMIT #{limit}
            ) t ORDER BY id ASC
            """)
    List<ImMessage> listLatest(@Param("conversationId") Long conversationId, @Param("limit") int limit);
}
