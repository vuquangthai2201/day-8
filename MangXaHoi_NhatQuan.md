# Thiết kế nhất quán dữ liệu cho mạng xã hội đơn giản

Nguyên tắc chung: chỉ dùng nhất quán mạnh ở chỗ có **bất biến** (tên đăng nhập duy nhất, bảo mật), còn lại dùng nhất quán yếu hơn để đổi lấy độ trễ thấp và khả năng mở rộng.

## Bảng tóm tắt

| Tính năng | Mô hình nhất quán | Lưu trữ đề xuất |
|---|---|---|
| Đăng ký/Đăng nhập | **Strong** (linearizable cho ghi thông tin xác thực) | PostgreSQL (primary); phiên làm việc ở Redis |
| Profile | **Read-your-writes** cho chủ tài khoản, **eventual** cho người khác | PostgreSQL cho dữ liệu; object storage (S3) + CDN cho ảnh |
| Đăng bài | **Read-your-writes** cho tác giả, **eventual** cho feed người theo dõi | PostgreSQL (quy mô nhỏ), Cassandra/DynamoDB khi lớn |
| Bình luận | **Causal** (kèm read-your-writes) | Cassandra/ScyllaDB (hoặc Postgres nếu nhỏ) |
| Đếm lượt thích | **Eventual** cho con số, **idempotent** cho từng lượt thích | Redis (bộ đếm) + bảng likes bền vững (Cassandra/Postgres) |

## 1. Đăng ký/Đăng nhập: Strong

**Vì sao:** Có hai bất biến không được vi phạm. Một là `username` phải duy nhất: nếu hai node cùng chấp nhận hai đăng ký trùng tên (do eventual) thì không có cách nào hợp nhất sau đó. Hai là bảo mật: đổi mật khẩu hoặc khóa tài khoản phải có hiệu lực ngay, nếu không kẻ tấn công vẫn đăng nhập được bằng mật khẩu cũ trên một replica chưa cập nhật.

**Đánh đổi:** Ghi phải đi qua một primary (có thể thêm đồng bộ sang standby) nên tốn latency và khó scale ghi đa vùng. Chấp nhận được vì đăng ký/đăng nhập là tần suất thấp so với đọc bài viết.

**Công nghệ:** PostgreSQL với `UNIQUE(username)`, băm mật khẩu bằng bcrypt/argon2, đọc xác thực từ primary. Phiên đăng nhập (token, danh sách thu hồi) để trong Redis vì có TTL và cần tốc độ.

## 2. Profile: Read-your-writes (session consistency)

**Vì sao:** Người dùng vừa đổi tên hoặc ảnh mà tải lại trang vẫn thấy bản cũ sẽ nghĩ là bị lỗi, nên chính chủ phải thấy thay đổi ngay. Còn người khác thấy cũ vài giây thì không sao, tức là eventual.

**Đánh đổi:** Phải định tuyến đọc của chính chủ về primary (hoặc đọc replica đã bắt kịp phiên bản vừa ghi), phức tạp hơn đọc replica tùy ý. Ảnh qua CDN có thể bị cache cũ, nên dùng URL có phiên bản (ví dụ thêm hash vào tên file) thay vì xóa cache.

**Công nghệ:** PostgreSQL cho tên và metadata (đọc nhiều, ghi ít, hợp cache); ảnh lưu ở S3/Object Storage phía sau CDN, DB chỉ giữ URL.

## 3. Đăng bài: Read-your-writes + eventual cho phân phối

**Vì sao:** Tác giả đăng xong phải thấy bài của mình ngay. Nhưng việc bài hiện trên feed của người theo dõi, trong tìm kiếm hay thông báo thì chấp nhận trễ vài giây, nên làm bất đồng bộ (fan-out qua hàng đợi).

**Đánh đổi:** Người theo dõi có thể thấy bài muộn hơn, hoặc feed của hai người thấy thứ tự hơi khác nhau. Đổi lại việc ghi bài rất nhanh và không bị chặn bởi số lượng người theo dõi.

**Công nghệ:** Với ứng dụng đơn giản, PostgreSQL là đủ và đơn giản nhất. Khi lượng bài rất lớn, ghi nhiều và truy vấn theo `user_id`/thời gian, chuyển sang Cassandra hoặc DynamoDB (khóa phân vùng theo user, sắp xếp theo thời gian). Feed dựng sẵn để ở Redis; chỉ mục tìm kiếm (Elasticsearch) cập nhật bất đồng bộ.

## 4. Bình luận: Causal consistency

**Vì sao:** Điều quan trọng là thứ tự nhân quả: bình luận chỉ được hiện sau bài viết mà nó thuộc về, và trả lời (reply) không được hiện trước bình luận gốc. Ngoài ra người vừa bình luận phải thấy ngay bình luận của mình (read-your-writes). Nhất quán mạnh toàn cục thì thừa, còn eventual thuần túy sẽ cho ra những cuộc hội thoại bị đảo thứ tự, khó hiểu.

**Đánh đổi:** Causal rẻ hơn strong nhưng phải mang thông tin thứ tự (timestamp/version) theo mỗi bình luận. Việc kiểm tra "bài viết có tồn tại không" phải làm ở tầng ứng dụng vì khác kho lưu trữ nên không có khóa ngoại.

**Công nghệ:** Cassandra/ScyllaDB, khóa phân vùng theo `post_id`, sắp xếp theo `timeuuid`. Mọi bình luận của một bài nằm cùng một phân vùng nên đọc ra theo đúng thứ tự thời gian rất nhanh. Ghi và đọc bằng `LOCAL_QUORUM` để đạt read-your-writes. Nếu quy mô nhỏ, Postgres bảng `comments(post_id, created_at)` vẫn hoàn toàn đủ.

## 5. Đếm lượt thích: Eventual

**Vì sao:** Không ai cần con số "1.204" chính xác từng giây; hiện "1.2K" trễ vài giây là chấp nhận được. Đây là dữ liệu ghi cực nhiều (bài viral có thể nhận hàng nghìn lượt thích mỗi giây), nên ép nhất quán mạnh sẽ tạo điểm nghẽn trên một bản ghi duy nhất.

**Đánh đổi:** Con số có thể lệch tạm thời giữa các người xem, và hội tụ về đúng sau một lúc. Riêng hành động "thích" của từng người thì phải **idempotent**: bấm hai lần, hoặc retry do lỗi mạng, vẫn chỉ tính một lần. Vì vậy cần bảng `likes(post_id, user_id)` với khóa chính để chống trùng. Người vừa bấm thích thấy nút đổi trạng thái ngay (cập nhật lạc quan ở client).

**Công nghệ:** Hai lớp. Lớp nguồn sự thật là bảng `likes` bền vững (Cassandra hoặc Postgres). Lớp hiển thị là bộ đếm Redis (`INCR`), được đối soát định kỳ với bảng likes. Với bài cực nóng, chia bộ đếm thành nhiều shard rồi cộng lại để tránh một khóa bị quá tải. Lưu ý không nên dùng counter column của Cassandra làm nguồn duy nhất, vì retry có thể đếm lố và không sửa được.

## Tổng kết

Sự đánh đổi nhất quán đi theo giá trị của dữ liệu. Dữ liệu danh tính và bảo mật cần đúng tuyệt đối nên chọn strong. Dữ liệu người dùng tự nhìn thấy (profile, bài đăng) chỉ cần read-your-writes. Dữ liệu có quan hệ thứ tự (bình luận) cần causal. Dữ liệu thống kê, ghi dồn dập (lượt thích) chấp nhận eventual. Nhờ đó hệ thống dành chi phí đắt đỏ của nhất quán mạnh đúng chỗ cần, và vẫn nhanh, dễ mở rộng ở những chỗ còn lại.
