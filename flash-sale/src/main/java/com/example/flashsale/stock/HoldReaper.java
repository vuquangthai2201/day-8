package com.example.flashsale.stock;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Trả hàng về cổng khi hold hết hạn (user bỏ giỏ). */
@Component
public class HoldReaper {

    private static final Logger log = LoggerFactory.getLogger(HoldReaper.class);
    private final HoldStore holds;

    public HoldReaper(HoldStore holds) {
        this.holds = holds;
    }

    @Scheduled(fixedDelay = 1000)
    public void reap() {
        try {
            for (String id : holds.productIds()) {
                long n = holds.reap(Long.parseLong(id), Instant.now());
                if (n > 0) log.info("Trả {} hold hết hạn của sản phẩm {}", n, id);
            }
        } catch (RuntimeException e) {
            log.warn("Reaper lỗi, thử lại ở lượt sau: {}", e.toString());
        }
    }
}
