package marketservice.mapper;

import marketservice.entity.Address;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AddressMapper {

    @Insert("INSERT INTO address(user_id, receiver_name, phone, province, city, district, detail, is_default) " +
            "VALUES(#{userId}, #{receiverName}, #{phone}, #{province}, #{city}, #{district}, #{detail}, #{isDefault})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Address address);

    @Select("SELECT * FROM address WHERE id=#{id}")
    Address findById(@Param("id") Long id);

    @Select("SELECT * FROM address WHERE user_id=#{userId} ORDER BY is_default DESC, id DESC")
    List<Address> listByUser(@Param("userId") Long userId);

    @Update("UPDATE address SET receiver_name=#{receiverName}, phone=#{phone}, province=#{province}, " +
            "city=#{city}, district=#{district}, detail=#{detail}, updated_at=NOW() " +
            "WHERE id=#{id} AND user_id=#{userId}")
    int update(Address address);

    @Delete("DELETE FROM address WHERE id=#{id} AND user_id=#{userId}")
    int delete(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE address SET is_default=0 WHERE user_id=#{userId}")
    int clearDefault(@Param("userId") Long userId);

    @Update("UPDATE address SET is_default=1, updated_at=NOW() WHERE id=#{id} AND user_id=#{userId}")
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}
