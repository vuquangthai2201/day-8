package com.example.warehouse;

import com.example.warehouse.dto.CreateInventoryRequest;
import com.example.warehouse.dto.InventoryHistoryResponse;
import com.example.warehouse.dto.UpdateInventoryRequest;
import com.example.warehouse.repository.InventoryRepository;
import com.example.warehouse.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceTest {

    @Autowired InventoryService service;
    @Autowired InventoryRepository repository;

    @Test
    void concurrentStockIn_noLostUpdate() throws Exception {
        long id = 1001L;
        service.create(new CreateInventoryRequest(id, "Pessimistic test", 10));

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> service.stockIn(id, 1)));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        assertEquals(60, repository.findById(id).orElseThrow().getQuantity());
    }

    @Test
    void update_withStaleVersion_isRejected() {
        long id = 1002L;
        service.create(new CreateInventoryRequest(id, "Old name", 5));

        var updated = service.update(id, new UpdateInventoryRequest("Name A", 0L));
        assertEquals(1L, updated.getVersion());

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> service.update(id, new UpdateInventoryRequest("Name B", 0L)));
        assertEquals("Name A", service.get(id).getProductName());
    }

    @Test
    void concurrentUpdate_sameVersion_onlyOneWins() throws Exception {
        long id = 1003L;
        service.create(new CreateInventoryRequest(id, "Original", 5));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> fa = pool.submit(() -> tryUpdate(start, id, "From A"));
        Future<Boolean> fb = pool.submit(() -> tryUpdate(start, id, "From B"));
        start.countDown();

        int successes = (fa.get() ? 1 : 0) + (fb.get() ? 1 : 0);
        pool.shutdown();
        assertEquals(1, successes);
    }

    @Test
    void stockIn_doesNotBumpVersion_andPutDoesNotOverwriteQuantity() {
        long id = 1004L;
        service.create(new CreateInventoryRequest(id, "Item", 10));

        service.stockIn(id, 5);
        assertEquals(0L, service.get(id).getVersion());      // stock-in khong tang version

        service.update(id, new UpdateInventoryRequest("Item v2", 0L));
        assertEquals(15, service.get(id).getQuantity());     // quantity khong bi ghi de
    }

    @Test
    void history_recordsEveryChange() {
        long id = 1005L;
        service.create(new CreateInventoryRequest(id, "Item", 10));
        service.stockIn(id, 5);
        service.update(id, new UpdateInventoryRequest("Item renamed", 0L));

        List<InventoryHistoryResponse> h = service.history(id);
        assertEquals(3, h.size());
        assertEquals("ADD", h.get(0).revisionType());
        assertEquals(10, h.get(0).quantityChange());
        assertEquals(15, h.get(1).quantity());
        assertEquals(5, h.get(1).quantityChange());
        assertEquals("Item renamed", h.get(2).productName());
        assertEquals(0, h.get(2).quantityChange());
    }

    private boolean tryUpdate(CountDownLatch start, long id, String name) throws Exception {
        start.await();
        try {
            service.update(id, new UpdateInventoryRequest(name, 0L));
            return true;
        } catch (ObjectOptimisticLockingFailureException e) {
            return false;
        }
    }
}
