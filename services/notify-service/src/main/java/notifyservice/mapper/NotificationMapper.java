package notifyservice.mapper;

import notifyservice.entity.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notification(user_id, from_user_id, type, ref_id, content) " +
            "VALUES(#{userId}, #{fromUserId}, #{type}, #{refId}, #{content})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);

    @Select("SELECT * FROM notification WHERE user_id=#{userId} AND type=#{type} " +
            "AND from_user_id=#{fromUserId} AND ref_id=#{refId} LIMIT 1")
    Notification findByEvent(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("fromUserId") Long fromUserId,
            @Param("refId") Long refId);

    @Select("SELECT * FROM notification WHERE id=#{id}")
    Notification findById(Long id);

    @Select("SELECT * FROM notification WHERE user_id=#{userId} " +
            "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Notification> listByUser(@Param("userId") Long userId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    @Update("UPDATE notification SET is_read=1 WHERE user_id=#{userId} AND is_read=0")
    int markAllRead(@Param("userId") Long userId);

    @Update("<script>" +
            "UPDATE notification SET is_read=1 " +
            "WHERE user_id=#{userId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int markReadByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
