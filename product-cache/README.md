# Product Cache Demo – Bài 12: DELETE + `@CacheEvict` + Redis Pub/Sub

Ứng dụng demo cache hai tầng cho sản phẩm:

| Tầng | Công nghệ | Phạm vi | Vai trò |
|------|-----------|---------|---------|
| L1 | Caffeine | trong JVM của từng instance | đọc cực nhanh |
| L2 | Redis (Spring Cache) | dùng chung mọi instance | giảm tải DB |
| DB | PostgreSQL | dùng chung | nguồn dữ liệu gốc |

## Kiến trúc

```
Controller -> ProductFacade ──► L1 Caffeine (LocalProductCache)
                   │
                   ├────────► ProductService ──► Redis L2 (@Cacheable/@CachePut/@CacheEvict)
                   │                         └─► PostgreSQL
                   └────────► Redis Pub/Sub (product-deletion, product-update)
                                   │
                    các instance khác nhận ──► xóa sản phẩm khỏi L1 của chúng
```

### Luồng đọc `GET /products/{id}`
1. Tìm trong L1 → trúng thì trả về ngay (`[L1   ] HIT`).
2. Trượt L1 → gọi `ProductService.getProduct` (`@Cacheable`): trúng L2 thì trả về, trượt L2 thì đọc DB (`[DB   ] ...`).
3. Kết quả được nạp lại vào L1.

### Luồng xóa `DELETE /products/{id}`  (nội dung bài 12)
1. `ProductService.deleteProduct`: kiểm tra tồn tại → xóa khỏi DB (404 nếu không có).
2. `@CacheEvict(cacheNames = "products", key = "#id")` xóa key khỏi **L2 (Redis)** sau khi method thành công.
3. `ProductFacade` xóa sản phẩm khỏi **L1 của instance hiện tại**.
4. `ProductCacheEventPublisher` publish lên channel **`product-deletion`** với payload `instanceId|productId`.
5. Mỗi instance khác nhận message trong `ProductCacheEventListener` và xóa sản phẩm khỏi **L1 của nó**.
   Message do chính instance gửi bị bỏ qua (vì L1 cục bộ đã xóa ở bước 3).

`PUT /products/{id}` hoạt động tương tự: `@CachePut` cập nhật L2, rồi xóa L1 và publish lên channel `product-update`.

### Vì sao publish nằm ở `ProductFacade` mà không nằm trong `ProductService`?
`@CacheEvict` chạy **sau** khi method của service trả về. Nếu publish ngay trong method, instance khác có thể nhận
message, đọc lại L2 (lúc đó chưa bị xóa) rồi nạp dữ liệu cũ vào L1. Đặt publish ở facade (sau khi proxy của service đã
xử lý xong cache) tránh được race này.

## Yêu cầu
- JDK 17+, Maven 3.9+
- Docker + Docker Compose

## Chạy thử

```bash
# 1. Bật Redis (cổng 6381) và PostgreSQL (cổng 5432)
docker compose up -d

# 2. Terminal A: instance 1 (khởi động trước để seed 3 sản phẩm mẫu)
mvn spring-boot:run

# 3. Terminal B: instance 2
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

# 4. Terminal C: chạy kịch bản demo (vừa chạy vừa xem log ở A và B)
./scripts/demo.sh
```

> Nếu cổng 5432 hoặc 6381 đã bị chiếm, đổi cổng trong `docker-compose.yml` và `application.yml`.

## API

| Method | URL | Mô tả | Kết quả |
|--------|-----|-------|---------|
| GET | `/products/{id}` | Đọc sản phẩm (L1 → L2 → DB) | 200 / 404 |
| POST | `/products` | Tạo sản phẩm | 201 |
| PUT | `/products/{id}` | Cập nhật (L2 `@CachePut`, L1 evict + publish) | 200 / 404 |
| DELETE | `/products/{id}` | Xóa (DB, L2 `@CacheEvict`, L1 evict + publish) | 204 / 404 |

Body mẫu cho POST/PUT:
```json
{ "name": "Laptop", "description": "Laptop 14 inch", "price": 1200.00 }
```

## Kiểm tra thủ công bài DELETE

```bash
# (tuỳ chọn) theo dõi message Pub/Sub
docker exec -it product-redis redis-cli SUBSCRIBE product-deletion product-update

curl localhost:8080/products/1        # A: L1 miss -> L2 miss -> DB
curl localhost:8081/products/1        # B: L1 miss -> L2 hit
curl localhost:8081/products/1        # B: L1 hit

curl -i -X DELETE localhost:8080/products/1     # 204

docker exec product-redis redis-cli EXISTS products::1   # 0  (L2 đã bị xóa)
curl -i localhost:8081/products/1                        # 404 (L1 của B đã bị xóa nhờ Pub/Sub)
```

Log mong đợi ở instance B ngay sau lệnh DELETE:
```
[SUB  ] 'product-deletion' received -> evicted product 1 from L1
```

## Chạy test
```bash
mvn test
```
`ProductFacadeTest` kiểm tra: lần đọc thứ hai trúng L1; delete chạy theo thứ tự DB/L2 → publish; L1 bị xóa sau
delete/update.

## Cấu trúc thư mục
```
src/main/java/com/example/productcache
├── ProductCacheApplication.java      @EnableCaching
├── config/        RedisConfig (CacheManager L2 + listener container), DataSeeder
├── controller/    ProductController
├── facade/        ProductFacade            (L1 + Pub/Sub)
├── service/       ProductService           (DB + L2 annotations)
├── cache/         LocalProductCache        (Caffeine)
├── pubsub/        InstanceId, ProductCacheEventPublisher, ProductCacheEventListener
├── entity/ dto/ repository/ exception/
```

## Lưu ý
- Pub/Sub của Redis là fire-and-forget: instance đang tắt hoặc mất kết nối sẽ bỏ lỡ message. Vì vậy L1 có TTL
  ngắn (`app.cache.l1.ttl-seconds`) để dữ liệu cũ tự hết hạn.
- Khởi động hai instance cùng lúc khi bảng còn trống có thể seed trùng dữ liệu mẫu; hãy khởi động instance A trước.
- Cache lưu `ProductResponse` (JSON) thay vì JPA entity để tránh lỗi lazy-loading/serialization.
