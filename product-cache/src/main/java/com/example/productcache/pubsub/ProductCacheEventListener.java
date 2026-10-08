package com.example.productcache.pubsub;

import com.example.productcache.cache.LocalProductCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Nhận message từ các channel product-deletion và product-update,
 * rồi xóa sản phẩm tương ứng khỏi L1 của instance này.
 */
@Component
public class ProductCacheEventListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheEventListener.class);

    private final LocalProductCache localCache;
    private final InstanceId instanceId;

    public ProductCacheEventListener(LocalProductCache localCache, InstanceId instanceId) {
        this.localCache = localCache;
        this.instanceId = instanceId;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        String[] parts = body.split("\\|", 2);
        if (parts.length != 2) {
            log.warn("[SUB  ] ignoring malformed message on '{}': {}", channel, body);
            return;
        }

        String senderId = parts[0];
        if (senderId.equals(instanceId.value())) {
            return; // message do chính instance này gửi: L1 đã được xóa cục bộ rồi
        }

        try {
            Long productId = Long.valueOf(parts[1]);
            localCache.evict(productId);
            log.info("[SUB  ] '{}' received -> evicted product {} from L1", channel, productId);
        } catch (NumberFormatException e) {
            log.warn("[SUB  ] ignoring message with invalid product id on '{}': {}", channel, body);
        }
    }
}
