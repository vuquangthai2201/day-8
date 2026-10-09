# Bài 15 – Hệ thống quản lý kho (Locking + Hibernate Envers)

Spring Boot 3.3 · Java 17 · Spring Data JPA · Hibernate Envers · H2

## Chạy
```bash
mvn spring-boot:run     # app chạy ở http://localhost:8080
mvn test                # chạy test (gồm test đồng thời)
```
H2 console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:warehouse`, user `sa`, không mật khẩu).
Khi khởi động có sẵn sản phẩm `productId = 1` ("Laptop Dell XPS", quantity 10).

## API
| Method | URL | Mô tả | Cơ chế |
|---|---|---|---|
| POST | `/inventories` | Tạo sản phẩm (phục vụ test) | – |
| GET | `/inventories/{id}` | Xem thông tin + version | – |
| POST | `/inventories/{id}/stock-in` | Nhập hàng `{"quantity": 5}` | **Pessimistic Lock** |
| PUT | `/inventories/{id}` | Sửa tên `{"productName": "...", "version": 0}` | **Optimistic Lock** |
| GET | `/inventories/{id}/history` | Lịch sử thay đổi | **Envers** |

Mã lỗi: `404` không tìm thấy · `400` dữ liệu không hợp lệ · `409` version cũ / sản phẩm trùng · `503` hết thời gian chờ lock.

## Thiết kế
- **Pessimistic**: `@Lock(PESSIMISTIC_WRITE)` → `SELECT ... FOR UPDATE`, các request nhập hàng xếp hàng trên cùng một dòng nên không mất cập nhật.
- **Optimistic**: cột `@Version`; client gửi kèm `version` đang giữ, khác version hiện tại → `409`. Việc so sánh thủ công là cần thiết vì Hibernate chỉ biết version của entity vừa load, không biết version client cầm. `saveAndFlush` giúp bắt thêm race xảy ra giữa lúc kiểm tra và lúc ghi.
- **`@DynamicUpdate`**: câu UPDATE chỉ chứa cột thực sự đổi. Nhờ vậy PUT (đọc `quantity` cũ) không ghi đè số lượng vừa được nhập hàng.
- **`@OptimisticLock(excluded = true)` trên `quantity`**: nhập hàng không tăng version, người đang sửa tên không bị `409` oan. Muốn mọi thay đổi đều tăng version thì bỏ annotation này.
- **Envers**: `@Audited` tạo bảng `inventory_aud` và `revinfo`. `quantityChange` được tính bằng cách so sánh với revision liền trước.

## Cấu trúc
```
src/main/java/com/example/warehouse
├── WarehouseApplication.java
├── config/DataSeeder.java
├── controller/InventoryController.java
├── dto/ (CreateInventoryRequest, StockInRequest, UpdateInventoryRequest, InventoryResponse, InventoryHistoryResponse)
├── entity/Inventory.java
├── exception/ (DuplicateProductException, GlobalExceptionHandler)
├── repository/InventoryRepository.java
└── service/InventoryService.java
src/test/java/com/example/warehouse
├── InventoryServiceTest.java      # test đồng thời, version, history
└── InventoryControllerTest.java   # test API qua MockMvc
```
