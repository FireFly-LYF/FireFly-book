package userservice.mapper;

import org.apache.ibatis.annotations.*;
import userservice.entity.RefreshToken;

@Mapper
public interface RefreshTokenMapper {

    @Insert("INSERT INTO refresh_token(user_id, token_hash, device_fingerprint, expires_at) " +
            "VALUES(#{userId}, #{tokenHash}, #{deviceFingerprint}, #{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RefreshToken token);

    @Select("SELECT * FROM refresh_token WHERE token_hash=#{tokenHash}")
    RefreshToken findByHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE refresh_token SET revoked=1 WHERE id=#{id} AND revoked=0")
    int revokeById(@Param("id") Long id);

    @Update("UPDATE refresh_token SET revoked=1 WHERE user_id=#{userId} AND revoked=0")
    int revokeAllByUserId(@Param("userId") Long userId);
}
