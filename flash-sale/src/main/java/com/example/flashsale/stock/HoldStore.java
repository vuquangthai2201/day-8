package com.example.flashsale.stock;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Cổng admission bằng Redis. Mọi thay đổi chạy trong Lua nên nguyên tử.
 * Redis chỉ là cổng chặn tải; nguồn sự thật về tồn kho luôn là Postgres.
 *
 * Key (hash tag {id} để cùng slot nếu dùng Redis Cluster):
 *   fs:{id}:avail   số lượng còn có thể giữ
 *   fs:{id}:holds   ZSET userId -> thời điểm hết hạn (epoch ms)
 *   fs:{id}:qty     HASH userId -> số lượng đang giữ
 */
@Component
public class HoldStore {

    private static final String PRODUCTS = "fs:products";

    private static final DefaultRedisScript<Long> RESERVE = script("""
            if redis.call('HEXISTS', KEYS[3], ARGV[1]) == 1 then return -2 end
            local avail = tonumber(redis.call('GET', KEYS[1]))
            if avail == nil then return -3 end
            local qty = tonumber(ARGV[2])
            if avail < qty then return -1 end
            redis.call('DECRBY', KEYS[1], qty)
            redis.call('HSET', KEYS[3], ARGV[1], qty)
            redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
            return qty
            """);

    private static final DefaultRedisScript<Long> BEGIN_CHECKOUT = script("""
            local q = redis.call('HGET', KEYS[2], ARGV[1])
            if not q then return -1 end
            redis.call('ZADD', KEYS[1], 'XX', ARGV[2], ARGV[1])
            return tonumber(q)
            """);

    private static final DefaultRedisScript<Long> FINISH = script("""
            local q = redis.call('HGET', KEYS[3], ARGV[1])
            if not q then return 0 end
            redis.call('HDEL', KEYS[3], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            if ARGV[2] == '1' then redis.call('INCRBY', KEYS[1], q) end
            return tonumber(q)
            """);

    private static final DefaultRedisScript<Long> REAP = script("""
            local users = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, 500)
            local n = 0
            for _, u in ipairs(users) do
              local q = tonumber(redis.call('HGET', KEYS[3], u)) or 0
              redis.call('INCRBY', KEYS[1], q)
              redis.call('HDEL', KEYS[3], u)
              redis.call('ZREM', KEYS[2], u)
              n = n + 1
            end
            return n
            """);

    private final StringRedisTemplate redis;

    public HoldStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static DefaultRedisScript<Long> script(String text) {
        return new DefaultRedisScript<>(text, Long.class);
    }

    private static String availKey(long id) { return "fs:{" + id + "}:avail"; }
    private static String holdsKey(long id) { return "fs:{" + id + "}:holds"; }
    private static String qtyKey(long id) { return "fs:{" + id + "}:qty"; }

    /** Khởi tạo nếu chưa có (không ghi đè khi app restart). */
    public void init(long productId, int stock) {
        redis.opsForSet().add(PRODUCTS, String.valueOf(productId));
        redis.opsForValue().setIfAbsent(availKey(productId), String.valueOf(stock));
    }

    /** Đặt lại hoàn toàn (admin, test). */
    public void reset(long productId, int stock) {
        redis.opsForSet().add(PRODUCTS, String.valueOf(productId));
        redis.delete(List.of(holdsKey(productId), qtyKey(productId)));
        redis.opsForValue().set(availKey(productId), String.valueOf(stock));
    }

    public Set<String> productIds() {
        Set<String> ids = redis.opsForSet().members(PRODUCTS);
        return ids == null ? Set.of() : ids;
    }

    public Long available(long productId) {
        String v = redis.opsForValue().get(availKey(productId));
        return v == null ? null : Long.valueOf(v);
    }

    public Integer heldQty(long productId, String userId) {
        Object v = redis.opsForHash().get(qtyKey(productId), userId);
        return v == null ? null : Integer.valueOf(v.toString());
    }

    /** >0: giữ thành công; -1: hết hàng; -2: user đã có hold; -3: đợt sale chưa khởi tạo. */
    public long reserve(long productId, String userId, int qty, Instant expiresAt) {
        return run(RESERVE, productId, userId, String.valueOf(qty), String.valueOf(expiresAt.toEpochMilli()));
    }

    /** Gia hạn hold khi bắt đầu checkout. Trả về qty, hoặc -1 nếu không có hold. */
    public long beginCheckout(long productId, String userId, Instant newExpiry) {
        Long r = redis.execute(BEGIN_CHECKOUT, List.of(holdsKey(productId), qtyKey(productId)),
                userId, String.valueOf(newExpiry.toEpochMilli()));
        return r == null ? -1 : r;
    }

    /** Kết thúc hold. restore=true: trả hàng về avail (thanh toán thất bại / bỏ). */
    public long finish(long productId, String userId, boolean restore) {
        return run(FINISH, productId, userId, restore ? "1" : "0");
    }

    /** Trả hàng của các hold quá hạn. */
    public long reap(long productId, Instant now) {
        return run(REAP, productId, String.valueOf(now.toEpochMilli()));
    }

    private long run(DefaultRedisScript<Long> script, long productId, String... args) {
        Long r = redis.execute(script,
                List.of(availKey(productId), holdsKey(productId), qtyKey(productId)), (Object[]) args);
        return r == null ? 0 : r;
    }
}
