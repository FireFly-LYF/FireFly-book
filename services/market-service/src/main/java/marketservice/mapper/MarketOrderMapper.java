package marketservice.mapper;

import marketservice.entity.MarketOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MarketOrderMapper {

    @Insert("INSERT INTO market_order(buyer_id, seller_id, status, amount_cents, address_snapshot, idem_key) " +
            "VALUES(#{buyerId}, #{sellerId}, #{status}, #{amountCents}, CAST(#{addressSnapshot} AS JSON), #{idemKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MarketOrder order);

    @Select("SELECT * FROM market_order WHERE id=#{id}")
    MarketOrder findById(@Param("id") Long id);

    @Select("SELECT * FROM market_order WHERE buyer_id=#{buyerId} AND idem_key=#{idemKey} LIMIT 1")
    MarketOrder findByBuyerAndIdemKey(@Param("buyerId") Long buyerId, @Param("idemKey") String idemKey);

    @Select("SELECT * FROM market_order WHERE buyer_id=#{buyerId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<MarketOrder> listByBuyer(@Param("buyerId") Long buyerId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM market_order WHERE seller_id=#{sellerId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<MarketOrder> listBySeller(@Param("sellerId") Long sellerId, @Param("offset") int offset, @Param("limit") int limit);

    @Update("UPDATE market_order SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND status=#{expectStatus}")
    int updateStatus(@Param("id") Long id, @Param("expectStatus") String expectStatus, @Param("status") String status);
}
