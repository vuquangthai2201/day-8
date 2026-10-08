package com.example.productcache.pubsub;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Định danh duy nhất của instance này; dùng để bỏ qua message do chính mình gửi. */
@Component
public class InstanceId {

    private final String value = UUID.randomUUID().toString();

    public String value() {
        return value;
    }
}
