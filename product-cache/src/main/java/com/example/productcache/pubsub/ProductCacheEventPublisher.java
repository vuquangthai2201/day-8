package com.example.productcache.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Gửi thông báo để các instance khác xóa sản phẩm khỏi L1.
 * Payload: "<instanceId>|<productId>".
 */
@Component
public class ProductCacheEventPublisher {

    public static final String DELETION_CHANNEL = "product-deletion";
    public static final String UPDATE_CHANNEL = "product-update";

    private static final Logger log = LoggerFactory.getLogger(ProductCacheEventPublisher.class);

    private final StringRedisTemplate redis;
    private final InstanceId instanceId;

    public ProductCacheEventPublisher(StringRedisTemplate redis, InstanceId instanceId) {
        this.redis = redis;
        this.instanceId = instanceId;
    }

    public void publishDeletion(Long productId) {
        publish(DELETION_CHANNEL, productId);
    }

    public void publishUpdate(Long productId) {
        publish(UPDATE_CHANNEL, productId);
    }

    private void publish(String channel, Long productId) {
        redis.convertAndSend(channel, instanceId.value() + "|" + productId);
        log.info("[PUB  ] published to '{}' for product {}", channel, productId);
    }
}
