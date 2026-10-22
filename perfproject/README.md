# perfproject – Bài 20: Phân tích & tối ưu hiệu năng (Spring Boot + JPA)

Yêu cầu: JDK 17+, Maven 3.9+ (không cần cài DB, dùng H2 in-memory).

## Chạy

```bash
mvn spring-boot:run
# hoặc
mvn clean package && java -jar target/perfproject-1.0.0.jar
```

Khi khởi động, `DataSeeder` nạp 1.000 author × 100 sách = 100.000 sách (đổi ở `application.properties`: `app.seed.*`).

## Endpoint

| Endpoint | Ý nghĩa |
|---|---|
| `GET /api/authors/slow` | TRƯỚC khi sửa: lỗi N+1 (1 + 1000 query) |
| `GET /api/authors/fast` | SAU khi sửa: `JOIN FETCH` (1 query) |
| `GET /api/books/slow?year=2000` | TRƯỚC: lấy sách rồi đọc tên tác giả (N+1) |
| `GET /api/books/fast?year=2000` | SAU: `@EntityGraph` + index `publication_year` |
| `GET /api/benchmark` | Tự đo số câu SQL và thời gian trung bình của tất cả kịch bản trước/sau (kể cả bật/tắt index) |

## Quan sát N+1 bằng log

Trong `application.properties` đổi:

```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
```

rồi gọi `/api/authors/slow`, sẽ thấy hàng nghìn câu `select ... from book where author_id=?`.

## Xem kế hoạch truy vấn (index)

H2 Console: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:perfdb`, user `sa`, password rỗng)

```sql
EXPLAIN SELECT COUNT(*) FROM book WHERE publication_year = 2000;
```

Có index sẽ dùng `IDX_BOOK_PUBLICATION_YEAR`; sau khi `DROP INDEX idx_book_publication_year` sẽ là quét toàn bộ bảng (`tableScan`).
