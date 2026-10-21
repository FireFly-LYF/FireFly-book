package marketservice.mapper;

import marketservice.entity.AfterSale;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AfterSaleMapper {

    @Insert("INSERT INTO after_sale(order_id, buyer_id, seller_id, type, reason, status, prev_order_status) " +
            "VALUES(#{orderId}, #{buyerId}, #{sellerId}, #{type}, #{reason}, #{status}, #{prevOrderStatus})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AfterSale afterSale);

    @Select("SELECT * FROM after_sale WHERE id=#{id}")
    AfterSale findById(@Param("id") Long id);

    @Select("SELECT * FROM after_sale WHERE order_id=#{orderId} AND status='PENDING' LIMIT 1")
    AfterSale findPendingByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE after_sale SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND status='PENDING'")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
