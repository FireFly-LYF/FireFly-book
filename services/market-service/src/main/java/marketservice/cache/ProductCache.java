package marketservice.cache;

import marketservice.entity.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Component
public class ProductCache {

    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);
    private static final String KEY_PREFIX = "market:product:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ProductCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Product get(Long id) {
        if (id == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(key(id));
            if (json == null || json.isBlank()) {
                return null;
            }
            return jsonMapper.readValue(json, Product.class);
        } catch (Exception e) {
            log.warn("读商品缓存失败 id={}: {}", id, e.getMessage());
            return null;
        }
    }

    public void put(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(product.getId()), jsonMapper.writeValueAsString(product), TTL);
        } catch (Exception e) {
            log.warn("写商品缓存失败 id={}: {}", product.getId(), e.getMessage());
        }
    }

    public void evict(Long id) {
        if (id == null) {
            return;
        }
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("删商品缓存失败 id={}: {}", id, e.getMessage());
        }
    }

    private static String key(Long id) {
        return KEY_PREFIX + id;
    }
}
