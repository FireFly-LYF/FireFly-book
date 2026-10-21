package marketservice.mapper;

import marketservice.entity.OrderItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("INSERT INTO order_item(order_id, product_id, title, cover_url, price_cents, quantity) " +
            "VALUES(#{orderId}, #{productId}, #{title}, #{coverUrl}, #{priceCents}, #{quantity})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderItem item);

    @Select("SELECT * FROM order_item WHERE order_id=#{orderId}")
    List<OrderItem> listByOrderId(@Param("orderId") Long orderId);

    @Select("<script>SELECT * FROM order_item WHERE order_id IN " +
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<OrderItem> listByOrderIds(@Param("orderIds") List<Long> orderIds);
}
