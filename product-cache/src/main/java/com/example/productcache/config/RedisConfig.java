package com.example.productcache.config;

import com.example.productcache.dto.ProductResponse;
import com.example.productcache.pubsub.ProductCacheEventListener;
import com.example.productcache.pubsub.ProductCacheEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
public class RedisConfig {

    /** L2 cache: Redis, giá trị lưu dạng JSON của ProductResponse, có TTL. */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     ObjectMapper objectMapper,
                                     @Value("${app.cache.l2.ttl-minutes:10}") long ttlMinutes) {

        Jackson2JsonRedisSerializer<ProductResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ProductResponse.class);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /** Đăng ký listener cho cả hai channel: product-deletion và product-update. */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ProductCacheEventListener listener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(ProductCacheEventPublisher.DELETION_CHANNEL));
        container.addMessageListener(listener, new ChannelTopic(ProductCacheEventPublisher.UPDATE_CHANNEL));
        return container;
    }
}
