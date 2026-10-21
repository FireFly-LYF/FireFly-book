package marketservice.mapper;

import marketservice.entity.Product;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProductMapper {

    @Insert("INSERT INTO product(seller_id, title, description, price_cents, stock, cover_url, status, idem_key) " +
            "VALUES(#{sellerId}, #{title}, #{description}, #{priceCents}, #{stock}, #{coverUrl}, #{status}, #{idemKey})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Product product);

    @Select("SELECT * FROM product WHERE id=#{id}")
    Product findById(@Param("id") Long id);

    @Select("SELECT * FROM product WHERE seller_id=#{sellerId} AND idem_key=#{idemKey} LIMIT 1")
    Product findBySellerAndIdemKey(@Param("sellerId") Long sellerId, @Param("idemKey") String idemKey);

    @Select("SELECT * FROM product WHERE status='ON' ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Product> listOnSale(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM product WHERE seller_id=#{sellerId} ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Product> listBySeller(@Param("sellerId") Long sellerId, @Param("offset") int offset, @Param("limit") int limit);

    @Update("UPDATE product SET title=#{title}, description=#{description}, price_cents=#{priceCents}, " +
            "stock=#{stock}, cover_url=#{coverUrl}, updated_at=NOW() WHERE id=#{id} AND seller_id=#{sellerId}")
    int update(Product product);

    @Update("UPDATE product SET status=#{status}, updated_at=NOW() WHERE id=#{id} AND seller_id=#{sellerId}")
    int updateStatus(@Param("id") Long id, @Param("sellerId") Long sellerId, @Param("status") String status);

    @Update("UPDATE product SET stock=stock-#{qty}, updated_at=NOW() WHERE id=#{id} AND stock>=#{qty} AND status='ON'")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    @Update("UPDATE product SET stock=stock+#{qty}, updated_at=NOW() WHERE id=#{id}")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}
