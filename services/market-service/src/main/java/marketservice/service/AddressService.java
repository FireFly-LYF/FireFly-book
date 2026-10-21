package marketservice.service;

import marketservice.dto.AddressRequest;
import marketservice.entity.Address;
import marketservice.mapper.AddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressService {

    private final AddressMapper addressMapper;

    public AddressService(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public List<Address> list(Long userId) {
        requireLogin(userId);
        return addressMapper.listByUser(userId);
    }

    @Transactional
    public Address create(Long userId, AddressRequest req) {
        requireLogin(userId);
        validate(req);
        boolean makeDefault = Boolean.TRUE.equals(req.getIsDefault())
                || addressMapper.listByUser(userId).isEmpty();
        if (makeDefault) {
            addressMapper.clearDefault(userId);
        }
        Address a = toEntity(userId, req);
        a.setIsDefault(makeDefault ? 1 : 0);
        addressMapper.insert(a);
        return addressMapper.findById(a.getId());
    }

    @Transactional
    public Address update(Long userId, Long id, AddressRequest req) {
        requireLogin(userId);
        Address existing = requireOwned(userId, id);
        validate(req);
        existing.setReceiverName(req.getReceiverName().trim());
        existing.setPhone(req.getPhone().trim());
        existing.setProvince(req.getProvince().trim());
        existing.setCity(req.getCity().trim());
        existing.setDistrict(trimToNull(req.getDistrict()));
        existing.setDetail(req.getDetail().trim());
        addressMapper.update(existing);
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            addressMapper.clearDefault(userId);
            addressMapper.setDefault(id, userId);
        }
        return addressMapper.findById(id);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireLogin(userId);
        requireOwned(userId, id);
        addressMapper.delete(id, userId);
    }

    @Transactional
    public Address setDefault(Long userId, Long id) {
        requireLogin(userId);
        requireOwned(userId, id);
        addressMapper.clearDefault(userId);
        addressMapper.setDefault(id, userId);
        return addressMapper.findById(id);
    }

    public Address requireOwned(Long userId, Long id) {
        Address a = addressMapper.findById(id);
        if (a == null || !userId.equals(a.getUserId())) {
            throw new IllegalArgumentException("地址不存在");
        }
        return a;
    }

    private static Address toEntity(Long userId, AddressRequest req) {
        Address a = new Address();
        a.setUserId(userId);
        a.setReceiverName(req.getReceiverName().trim());
        a.setPhone(req.getPhone().trim());
        a.setProvince(req.getProvince().trim());
        a.setCity(req.getCity().trim());
        a.setDistrict(trimToNull(req.getDistrict()));
        a.setDetail(req.getDetail().trim());
        return a;
    }

    private static void validate(AddressRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        requireText(req.getReceiverName(), "收件人", 64);
        requireText(req.getPhone(), "手机号", 32);
        requireText(req.getProvince(), "省", 64);
        requireText(req.getCity(), "市", 64);
        requireText(req.getDetail(), "详细地址", 256);
        if (req.getDistrict() != null && req.getDistrict().length() > 64) {
            throw new IllegalArgumentException("区最长 64 字");
        }
    }

    private static void requireText(String v, String label, int max) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (v.trim().length() > max) {
            throw new IllegalArgumentException(label + "最长 " + max + " 字");
        }
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
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
