package marketservice.mapper;

import marketservice.entity.CartItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CartItemMapper {

    @Select("SELECT * FROM cart_item WHERE user_id=#{userId} ORDER BY id DESC")
    List<CartItem> listByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM cart_item WHERE user_id=#{userId} AND product_id=#{productId} LIMIT 1")
    CartItem find(@Param("userId") Long userId, @Param("productId") Long productId);

    @Insert("INSERT INTO cart_item(user_id, product_id, quantity) VALUES(#{userId}, #{productId}, #{quantity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CartItem item);

    @Update("UPDATE cart_item SET quantity=#{quantity}, updated_at=NOW() WHERE user_id=#{userId} AND product_id=#{productId}")
    int updateQuantity(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") int quantity);

    @Delete("DELETE FROM cart_item WHERE user_id=#{userId} AND product_id=#{productId}")
    int delete(@Param("userId") Long userId, @Param("productId") Long productId);

    @Delete("<script>DELETE FROM cart_item WHERE user_id=#{userId} AND product_id IN " +
            "<foreach collection='productIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deleteByProducts(@Param("userId") Long userId, @Param("productIds") List<Long> productIds);
}
