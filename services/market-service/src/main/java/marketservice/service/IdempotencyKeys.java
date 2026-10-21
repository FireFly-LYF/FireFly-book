package marketservice.service;

final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim();
        if (key.isEmpty()) {
            return null;
        }
        if (key.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key 最长 64 字符");
        }
        return key;
    }
}
