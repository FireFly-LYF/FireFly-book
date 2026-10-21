package marketservice.mapper;

import marketservice.entity.Payment;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentMapper {

    @Insert("INSERT INTO payment(order_id, amount_cents, channel, status, paid_at) " +
            "VALUES(#{orderId}, #{amountCents}, #{channel}, #{status}, #{paidAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Payment payment);

    @Select("SELECT * FROM payment WHERE order_id=#{orderId} LIMIT 1")
    Payment findByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE payment SET status=#{status}, refunded_at=NOW() WHERE order_id=#{orderId} AND status='PAID'")
    int markRefunded(@Param("orderId") Long orderId, @Param("status") String status);
}
