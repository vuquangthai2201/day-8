package com.example.flashsale.stock;

import java.time.Instant;

public record StockView(long productId, long available, Instant asOf, String source) {}
