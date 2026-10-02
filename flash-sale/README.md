# flash-sale

Hệ thống Flash Sale: Spring Boot 3 (Java 21) + PostgreSQL + Redis. Thiết kế chi tiết nằm ở [DESIGN.md](DESIGN.md) (PACELC, topology, knobs, sơ đồ Mermaid, rủi ro).

## Chạy

```bash
docker compose up -d              # Postgres + Redis
mvn test                          # unit test; test tích hợp (Testcontainers) cần Docker
mvn spring-boot:run               # http://localhost:8080
```

Tùy chọn: replica đọc bất đồng bộ

```bash
docker compose --profile replica up -d
APP_REPLICA_URL=jdbc:postgresql://localhost:5433/flashsale mvn spring-boot:run
```

Bật sync replication cho checkout: bỏ comment hai dòng `synchronous_*` trong `docker-compose.yml` (cần replica đang chạy, nếu không ghi sẽ treo).

## Thử API

```bash
curl localhost:8080/products/1/stock

curl -X POST localhost:8080/cart/items -H 'Content-Type: application/json' -H 'X-User-Id: alice' \
     -d '{"productId":1,"qty":1}'

curl -X POST localhost:8080/checkout -H 'Content-Type: application/json' -H 'X-User-Id: alice' \
     -H 'Idempotency-Key: alice-order-1' -d '{"productId":1}'
# gọi lại cùng Idempotency-Key: nhận lại đúng đơn cũ, không trừ kho lần hai

curl localhost:8080/orders/<id>
```

Đặt lại tồn kho (demo/test): `curl -X POST localhost:8080/admin/products/1/stock -H 'Content-Type: application/json' -d '{"stock":1000}'`

| Mã | Ý nghĩa |
|---|---|
| 201 / 200 | Giữ hàng thành công / user đã có hold |
| 202 | Checkout nhận, thanh toán đang xử lý (poll `/orders/{id}`) |
| 409 | `SOLD_OUT`, `NO_ACTIVE_HOLD`, `ALREADY_PURCHASED` |
| 503 | Cổng Redis không khả dụng (có `Retry-After`) |

## Test và đo tải

- `OversellIntegrationTest`: 500 user tranh 50 sản phẩm phải ra đúng 50 đơn PAID; và khi Redis đếm sai (1000 so với DB 5) thì DB vẫn chỉ cho 5 đơn.
- `loadtest/flashsale.js` (k6): đặt ngưỡng P95 listing < 80ms, add-to-cart và checkout < 120ms.
  `k6 run loadtest/flashsale.js`

## Cấu trúc

```
src/main/java/com/example/flashsale/
  stock/     HoldStore (Lua), HoldReaper, StockViewService (listing), SaleInitializer
  cart/      CartService
  checkout/  CheckoutService, OrderRepository, PendingOrderReconciler
  catalog/   ProductRepository (UPDATE có điều kiện)
  payment/   PaymentGateway (idempotent), SimulatedPaymentGateway
  error/     exception + ApiExceptionHandler
  admin/     AdminController (demo)
  config/    JdbcConfig (primary + replica)
```
