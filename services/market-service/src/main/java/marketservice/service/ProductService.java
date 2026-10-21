package marketservice.service;

import marketservice.cache.ProductCache;
import marketservice.client.UserClient;
import marketservice.dto.CreateProductRequest;
import marketservice.dto.ProductDetailResponse;
import marketservice.dto.UpdateProductRequest;
import marketservice.entity.Product;
import marketservice.mapper.ProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductCache productCache;
    private final UserClient userClient;

    public ProductService(ProductMapper productMapper, ProductCache productCache, UserClient userClient) {
        this.productMapper = productMapper;
        this.productCache = productCache;
        this.userClient = userClient;
    }

    @Transactional
    public Product create(Long sellerId, CreateProductRequest req, String idempotencyKey) {
        if (sellerId == null) {
            throw new IllegalArgumentException("未登录");
        }
        validateCreate(req);
        String idemKey = IdempotencyKeys.normalize(idempotencyKey);
        if (idemKey != null) {
            Product existed = productMapper.findBySellerAndIdemKey(sellerId, idemKey);
            if (existed != null) {
                return existed;
            }
        }
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setTitle(req.getTitle().trim());
        p.setDescription(trimToNull(req.getDescription()));
        p.setPriceCents(req.getPriceCents());
        p.setStock(req.getStock());
        p.setCoverUrl(trimToNull(req.getCoverUrl()));
        p.setStatus(Product.STATUS_ON);
        p.setIdemKey(idemKey);
        try {
            productMapper.insert(p);
        } catch (DuplicateKeyException e) {
            Product existed = productMapper.findBySellerAndIdemKey(sellerId, idemKey);
            if (existed != null) {
                return existed;
            }
            throw e;
        }
        return productMapper.findById(p.getId());
    }

    public List<Product> listOnSale(int page, int size) {
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 50);
        return productMapper.listOnSale((p - 1) * s, s);
    }

    public List<Product> listMine(Long sellerId, int page, int size) {
        if (sellerId == null) {
            throw new IllegalArgumentException("未登录");
        }
        int p = page < 1 ? 1 : page;
        int s = size < 1 ? 20 : Math.min(size, 50);
        return productMapper.listBySeller(sellerId, (p - 1) * s, s);
    }

    public ProductDetailResponse getDetail(Long id) {
        Product p = getCached(id);
        if (p == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        Map<Long, ProductDetailResponse.SellerSummary> sellers =
                userClient.batchSummaries(List.of(p.getSellerId()));
        return ProductDetailResponse.from(p, sellers.get(p.getSellerId()));
    }

    public Product requireOnSale(Long id) {
        Product p = getCached(id);
        if (p == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        if (!Product.STATUS_ON.equals(p.getStatus())) {
            throw new IllegalArgumentException("商品已下架");
        }
        return p;
    }

    public Product getCached(Long id) {
        if (id == null) {
            return null;
        }
        Product cached = productCache.get(id);
        if (cached != null) {
            return cached;
        }
        Product p = productMapper.findById(id);
        if (p != null) {
            productCache.put(p);
        }
        return p;
    }

    @Transactional
    public Product update(Long sellerId, Long id, UpdateProductRequest req) {
        Product existing = requireOwned(sellerId, id);
        if (req.getTitle() != null) {
            String title = req.getTitle().trim();
            if (title.isEmpty() || title.length() > 128) {
                throw new IllegalArgumentException("标题 1~128 字");
            }
            existing.setTitle(title);
        }
        if (req.getDescription() != null) {
            if (req.getDescription().length() > 2000) {
                throw new IllegalArgumentException("描述最长 2000 字");
            }
            existing.setDescription(trimToNull(req.getDescription()));
        }
        if (req.getPriceCents() != null) {
            if (req.getPriceCents() <= 0) {
                throw new IllegalArgumentException("价格必须大于 0");
            }
            existing.setPriceCents(req.getPriceCents());
        }
        if (req.getStock() != null) {
            if (req.getStock() < 0) {
                throw new IllegalArgumentException("库存不能为负");
            }
            existing.setStock(req.getStock());
        }
        if (req.getCoverUrl() != null) {
            existing.setCoverUrl(trimToNull(req.getCoverUrl()));
        }
        productMapper.update(existing);
        productCache.evict(id);
        return productMapper.findById(id);
    }

    @Transactional
    public Product setStatus(Long sellerId, Long id, String status) {
        requireOwned(sellerId, id);
        if (!Product.STATUS_ON.equals(status) && !Product.STATUS_OFF.equals(status)) {
            throw new IllegalArgumentException("非法状态");
        }
        int n = productMapper.updateStatus(id, sellerId, status);
        if (n == 0) {
            throw new IllegalArgumentException("更新失败");
        }
        productCache.evict(id);
        return productMapper.findById(id);
    }

    public void evictCache(Long id) {
        productCache.evict(id);
    }

    private Product requireOwned(Long sellerId, Long id) {
        if (sellerId == null) {
            throw new IllegalArgumentException("未登录");
        }
        Product p = productMapper.findById(id);
        if (p == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        if (!sellerId.equals(p.getSellerId())) {
            throw new IllegalArgumentException("无权操作该商品");
        }
        return p;
    }

    private static void validateCreate(CreateProductRequest req) {
        if (req == null || req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (req.getTitle().trim().length() > 128) {
            throw new IllegalArgumentException("标题最长 128 字");
        }
        if (req.getDescription() != null && req.getDescription().length() > 2000) {
            throw new IllegalArgumentException("描述最长 2000 字");
        }
        if (req.getPriceCents() == null || req.getPriceCents() <= 0) {
            throw new IllegalArgumentException("价格必须大于 0");
        }
        if (req.getStock() == null || req.getStock() < 0) {
            throw new IllegalArgumentException("库存不能为负");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
