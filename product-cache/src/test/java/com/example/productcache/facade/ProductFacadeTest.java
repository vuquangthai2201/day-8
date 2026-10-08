package com.example.productcache.facade;

import com.example.productcache.cache.LocalProductCache;
import com.example.productcache.dto.ProductRequest;
import com.example.productcache.dto.ProductResponse;
import com.example.productcache.pubsub.ProductCacheEventPublisher;
import com.example.productcache.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    ProductService productService;

    @Mock
    ProductCacheEventPublisher publisher;

    LocalProductCache localCache;
    ProductFacade facade;

    final ProductResponse laptop = new ProductResponse(1L, "Laptop", "desc", new BigDecimal("1200.00"));

    @BeforeEach
    void setUp() {
        localCache = new LocalProductCache(60, 100);
        facade = new ProductFacade(productService, localCache, publisher);
    }

    @Test
    void secondReadIsServedFromL1() {
        when(productService.getProduct(1L)).thenReturn(laptop);

        facade.getProduct(1L);
        facade.getProduct(1L);

        verify(productService, times(1)).getProduct(1L);   // lần 2 trúng L1
    }

    @Test
    void deleteRemovesFromDbThenL1ThenPublishes() {
        when(productService.getProduct(1L)).thenReturn(laptop);
        facade.getProduct(1L);                               // nạp L1

        facade.deleteProduct(1L);

        InOrder order = inOrder(productService, publisher);
        order.verify(productService).deleteProduct(1L);      // DB + L2 trước
        order.verify(publisher).publishDeletion(1L);         // rồi mới publish

        facade.getProduct(1L);                               // L1 đã bị xóa -> phải hỏi lại service
        verify(productService, times(2)).getProduct(1L);
    }

    @Test
    void updateEvictsL1AndPublishes() {
        ProductRequest request = new ProductRequest("Laptop Pro", "desc", new BigDecimal("1500.00"));
        when(productService.getProduct(1L)).thenReturn(laptop);
        when(productService.updateProduct(eq(1L), any())).thenReturn(
                new ProductResponse(1L, "Laptop Pro", "desc", new BigDecimal("1500.00")));
        facade.getProduct(1L);

        facade.updateProduct(1L, request);

        verify(publisher).publishUpdate(1L);
        facade.getProduct(1L);
        verify(productService, times(2)).getProduct(1L);
    }
}
