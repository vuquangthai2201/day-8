# REPORT – Bài 20: Phân tích & tối ưu hiệu năng

## 1. Các vấn đề đã tìm thấy

### Vấn đề 1: Lỗi N+1 query
- **Vị trí:** quan hệ `Author.books` (`@OneToMany`, LAZY). Khi service lấy danh sách `Author` bằng `findAll()` rồi duyệt `author.getBooks()` (xem `AuthorService.getAuthorsSlow`), Hibernate chạy:
  - 1 query lấy N author, và
  - N query `select ... from book where author_id = ?` (mỗi author một query).
  - Tương tự với `Book.author` (LAZY) khi lấy danh sách sách rồi gọi `book.getAuthor().getName()` (xem `BookService.getBooksByYearSlow`).
- **Hậu quả:** 1.000 author → 1.001 query; thời gian tăng tuyến tính theo số bản ghi, tốn round-trip tới DB.

### Vấn đề 2: Query thiếu index
- **Vị trí:** truy vấn lọc theo `Book.publicationYear` (`findByPublicationYear`, `countByPublicationYear`) và join/lọc theo `author_id`. Cả hai cột đều không có index.
- **Hậu quả:** DB phải quét toàn bộ bảng `book`; càng nhiều dữ liệu càng chậm. Với PostgreSQL, khoá ngoại `author_id` cũng không được tự động đánh index.

### Vấn đề 3 (phụ): `@Data` trên entity có quan hệ hai chiều
- `@Data` sinh `equals/hashCode/toString` dựa trên **mọi** field, kể cả `books` và `author`. Việc này:
  - có thể kích hoạt lazy-load ngoài ý muốn (thêm query ẩn) khi đưa entity vào `Set`/`Map`/log,
  - gây đệ quy vô hạn `Author.hashCode → Book.hashCode → Author.hashCode …` (StackOverflowError). `@ToString.Exclude` chỉ chặn được `toString`, không chặn được `hashCode/equals`.

## 2. Cách xác định vấn đề

| Vấn đề | Cách phát hiện |
|---|---|
| N+1 | Bật log SQL, gọi `/api/authors/slow` và đếm số câu `select` lặp lại gần như giống hệt nhau, chỉ khác tham số `author_id`. Xác nhận bằng Hibernate Statistics (`getPrepareStatementCount()` ≈ 1001). |
| Thiếu index | Chạy `EXPLAIN` (H2/PostgreSQL: `EXPLAIN ANALYZE`) cho `select count(*) from book where publication_year = 2000` → quét toàn bộ bảng thay vì dùng index. |
| `@Data` | Đọc code: entity hai chiều + `@Data` ⇒ `hashCode` đi qua `books`; xác nhận bằng cách gọi `hashCode()` hoặc đưa entity vào `HashSet`. |

Cấu hình bật log (`application.properties`):

```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
spring.jpa.properties.hibernate.generate_statistics=true
```

## 3. Giải pháp đã áp dụng

1. **Sửa N+1** (`AuthorRepository`, `BookRepository`):
   - `@Query("SELECT DISTINCT a FROM Author a LEFT JOIN FETCH a.books")` → 1 query duy nhất cho author + books.
   - `@EntityGraph(attributePaths = "author")` cho truy vấn Book khi cần thông tin author.
   - Thêm `@BatchSize(size = 50)` trên `Author.books` làm lớp bảo vệ: nếu vẫn lazy-load thì chỉ còn ⌈N/50⌉ query thay vì N.
   - Lưu ý: không dùng `JOIN FETCH` kèm phân trang (`Pageable`) trên collection, vì Hibernate sẽ phân trang trong bộ nhớ; trường hợp đó dùng `@BatchSize`.
2. **Thêm index** (`Book`):
   ```java
   @Table(name = "book", indexes = {
       @Index(name = "idx_book_publication_year", columnList = "publicationYear"),
       @Index(name = "idx_book_author_id", columnList = "author_id")
   })
   ```
   Trên production nên dùng migration (Flyway/Liquibase):
   ```sql
   CREATE INDEX idx_book_publication_year ON book (publication_year);
   CREATE INDEX idx_book_author_id ON book (author_id);
   ```
3. **Bỏ `@Data`** trên entity, thay bằng `@Getter/@Setter` và tự viết `equals/hashCode/toString` chỉ dựa trên `id` (không chạm vào quan hệ).

## 4. (Nâng cao) So sánh trước và sau

Project có sẵn endpoint tự đo. Chạy `mvn spring-boot:run` rồi gọi:

```bash
curl http://localhost:8080/api/benchmark
```

Mỗi kịch bản chạy 1 lần warm-up (bỏ qua) + 5 lần đo; kết quả gồm số câu SQL (`sqlStatements`) và thời gian trung bình (`avgMs`). Dữ liệu mẫu: 1.000 author × 100 sách = 100.000 sách (H2 in-memory).

Điền kết quả vào bảng (không dùng số ước đoán):

| Kịch bản | Số SQL trước | Số SQL sau | Thời gian trước (ms) | Thời gian sau (ms) |
|---|---|---|---|---|
| Author kèm books (N+1 → JOIN FETCH) | … | … | … | … |
| Sách theo năm kèm tên author (N+1 → EntityGraph) | … | … | … | … |
| Đếm sách theo năm (không index → có index) | … | … | … | … |

Lưu ý khi đọc kết quả: trên H2 in-memory, truy vấn rất nhanh nên chênh lệch thời gian của index nhỏ hơn nhiều so với DB thật qua mạng; số lượng câu SQL mới là chỉ số ổn định nhất cho lỗi N+1. Có thể tăng `app.seed.books-per-author` để thấy rõ tác động của index.

## 5. Kết luận
N+1 là vấn đề có ảnh hưởng lớn nhất vì số query tăng theo số bản ghi; index giúp thời gian mỗi query không tăng theo kích thước bảng; bỏ `@Data` loại bỏ truy vấn ẩn và lỗi đệ quy tiềm ẩn.
