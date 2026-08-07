package mediaservice.mapper;

import mediaservice.entity.MediaAsset;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MediaAssetMapper {

    @Insert("INSERT INTO media_asset(user_id, original_name, content_type, size_bytes, url) " +
            "VALUES(#{userId}, #{originalName}, #{contentType}, #{sizeBytes}, #{url})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MediaAsset asset);

    @Select("SELECT * FROM media_asset WHERE id=#{id}")
    MediaAsset findById(Long id);
}
