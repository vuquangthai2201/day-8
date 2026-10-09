# gc-demo – Bài 13: tìm lỗi hiệu năng của `/process` và chụp bằng chứng

Project Spring Boot nhỏ để **tái hiện lỗi nối chuỗi trong vòng lặp** (High GC churn / O(n²)) và thu thập bằng chứng bằng VisualVM, log GC, `jstat`.

| Endpoint | Mô tả |
|----------|-------|
| `GET /process` | **Bản lỗi** nguyên văn từ đề bài (`result += " " + i`, 150.000 vòng). |
| `GET /process-fixed` | **Bản đã sửa** dùng `StringBuilder`. |
| `GET /ping` | Endpoint siêu nhẹ, dùng để thấy hệ thống bị chậm lan sang endpoint khác khi bản lỗi đang chạy. |

Cả hai endpoint trả về cùng một kết quả: `Processing finished in XXms. Result length: 938890`.

```
gc-demo/
├── pom.xml
├── README.md                  <- file này
├── markdown.md                <- báo cáo phân tích (đã có số liệu đo mẫu)
├── src/main/java/com/example/gcdemo/
│   ├── GcDemoApplication.java
│   └── ProcessController.java
├── src/test/java/...          <- test cho bản đã sửa
├── scripts/
│   ├── run.sh / run.bat       <- chạy app với heap 256 MB + log GC
│   ├── load.sh                <- gửi request (tuần tự hoặc đồng thời)
│   └── gc-summary.sh          <- tóm tắt log GC thành vài con số
├── logs/                      <- gc.log được ghi vào đây
└── evidence/                  <- để bạn lưu screenshot
```

---

## PHẦN A – CHUẨN BỊ (làm một lần)

### A1. Cài các công cụ

| Công cụ | Kiểm tra | Ghi chú |
|---------|----------|---------|
| JDK 17 trở lên (**JDK, không phải JRE**) | `java -version` và `jps -l` phải chạy được | Cần `jps`, `jstat`, `jcmd` đi kèm JDK |
| Maven 3.9+ | `mvn -version` | |
| curl | `curl --version` | Windows 10/11 đã có sẵn |
| VisualVM | Tải bản standalone tại https://visualvm.github.io | Từ JDK 9 trở đi VisualVM **không còn đi kèm JDK**, phải tải riêng |

**Windows:** các script `.sh` chạy được trong **Git Bash** hoặc WSL. Nếu không muốn dùng, dùng `scripts\run.bat` và các lệnh `curl` thủ công ghi ở mục "Dành cho Windows" bên dưới.

### A2. Cài plugin Visual GC cho VisualVM
1. Mở VisualVM (giải nén file đã tải, chạy `bin/visualvm` hoặc `bin\visualvm.exe`).
2. Menu **Tools → Plugins → tab Available Plugins**.
3. Tích **Visual GC** → **Install** → làm theo hướng dẫn → khởi động lại VisualVM nếu được hỏi.

(Nếu không thấy trong danh sách, kiểm tra tab *Installed*: có thể đã cài sẵn.)

### A3. Build project
```bash
cd gc-demo
chmod +x scripts/*.sh        # chỉ cần làm một lần (Linux/macOS/Git Bash)
mvn -DskipTests package
```
Thành công khi thấy `BUILD SUCCESS` và file `target/gc-demo-0.0.1-SNAPSHOT.jar`.

> Không dùng `mvn spring-boot:run` để đo. Khi đó VisualVM sẽ thấy cả tiến trình Maven lẫn app, cờ JVM cũng khó kiểm soát. Hãy dùng `java -jar` thông qua script bên dưới.

---

## PHẦN B – THU THẬP BẰNG CHỨNG CHO BẢN LỖI

Mở **3 cửa sổ**: Terminal 1 (chạy app), Terminal 2 (gửi request), VisualVM.

Đặt tên file ảnh theo checklist ở Phần D (ví dụ `evidence/02-monitor-bad.png`) để dễ chèn vào báo cáo.

### Bước 1 – Chạy ứng dụng (Terminal 1)
```bash
./scripts/run.sh
```
Windows: `scripts\run.bat`

Script chạy: `java -Xms256m -Xmx256m -XX:+UseG1GC -Xlog:gc*:file=logs/gc.log:... -jar target/gc-demo-0.0.1-SNAPSHOT.jar`

- Heap cố định 256 MB để đồ thị heap rõ và GC xảy ra thường xuyên.
- Log GC đầy đủ ghi ở `logs/gc.log`, **không xoay vòng** (`filecount=0`) để không mất phần đầu log.

Đợi đến khi thấy dòng `Started GcDemoApplication in ... seconds`.

Kiểm tra nhanh (Terminal 2): `curl localhost:8080/ping` → `pong`.

### Bước 2 – Gắn VisualVM vào tiến trình
1. Mở VisualVM. Ở cột trái, mục **Local**, tìm tiến trình tên **`gc-demo-0.0.1-SNAPSHOT.jar`** → **bấm đúp**.
2. Cửa sổ bên phải mở ra với các tab: **Overview, Monitor, Threads, Sampler, Profiler, Visual GC**.

   *Không thấy tiến trình?* Xem mục "Xử lý sự cố" cuối file.

### Bước 3 – Chụp ảnh nền (app đang rảnh)
1. Tab **Overview**: chụp để ghi lại **cờ JVM** (`-Xms256m -Xmx256m -XX:+UseG1GC ...`) và phiên bản Java → `01-overview.png`.
2. Tab **Monitor**: chụp lúc app đang rảnh → `02a-monitor-idle.png`. Heap phẳng, CPU gần 0%.

### Bước 4 – Gây tải bản lỗi (Terminal 2)
```bash
./scripts/load.sh /process 5 1
```
Gửi 5 request **tuần tự** tới `/process`. Mỗi request mất khoảng vài giây đến hơn 10 giây tùy máy, nên tổng thời gian khoảng 15–60 giây, đủ để đồ thị hiện rõ.

Kết quả mong đợi trong Terminal 2:
```
request 1 -> HTTP 200 trong 10.4s
...
```
(Con số tuyệt đối khác nhau tuỳ máy; mẫu đo của mình là ~10,4 giây trên máy 1 CPU.)

### Bước 5 – Chụp ảnh **trong lúc** request đang chạy
Làm nhanh, ngay sau khi chạy lệnh ở bước 4.

**5a. Tab Monitor** → `02-monitor-bad.png`
- Biểu đồ **Heap**: đường *Used heap* dạng **răng cưa dày đặc**, đỉnh cao vài trăm MB (gần mức 256 MB), đáy luôn quay về rất thấp.
- Biểu đồ **CPU**: một lõi CPU gần 100% (nếu máy nhiều lõi, tổng CPU có thể chỉ ~1/số lõi, vẫn bình thường vì vòng lặp chạy đơn luồng).
- Ghi chú vào ảnh (tùy chọn): "heap răng cưa, đáy về thấp = không leak".

**5b. Tab Visual GC** → `03-visualgc-bad.png`
- Các ô **Eden / Old Gen / Survivor** cho thấy Eden đầy rồi trống liên tục.
- Ô **GC Time / Collections** tăng nhanh (số lần GC tăng theo từng giây).
- (Với G1, nhiều đối tượng lớn có thể làm vùng Old/Humongous nhấp nháy.)

**5c. Tab Sampler → CPU** → `04-sampler-cpu-bad.png`
1. Mở tab **Sampler**, bấm nút **CPU**.
2. Giữ nguyên trong khi request đang chạy (chạy lại bước 4 nếu đã hết).
3. Bấm **Snapshot** (hoặc chụp ngay), sắp xếp theo cột **Self time**.
4. Bạn sẽ thấy thời gian dồn vào các hàm ghép chuỗi/sao chép mảng (ví dụ nhóm `StringConcatHelper`, `String.concat`, `Arrays.copyOf`, `System.arraycopy`, tùy phiên bản JDK) và nằm bên dưới `ProcessController.processData`.

**5d. Tab Threads** → `04b-threads-bad.png` (tùy chọn)
- Một thread của Tomcat (`http-nio-8080-exec-N`) ở trạng thái **Running** liên tục.

### Bước 6 – Chứng minh **không phải memory leak**
Sau khi các request đã xong:
1. Tab **Monitor** → bấm nút **Perform GC**.
2. Heap quay về mức rất thấp (vài MB), **không tích lũy** → chụp `05-after-gc-no-leak.png`.

Đây là điểm mấu chốt trong phần "Vấn đề bạn tìm thấy là gì?": đường *Used heap* răng cưa nhưng đáy không nâng lên = **GC churn**, không phải leak.

### Bước 7 – Chụp log GC
```bash
./scripts/gc-summary.sh
```
Windows (PowerShell):
```powershell
(Select-String -Path logs\gc.log -Pattern "\] GC\(\d+\) Pause.*ms$").Count
(Select-String -Path logs\gc.log -Pattern "Humongous Allocation").Count
```

Kết quả mẫu (đo thật của mình trên bản lỗi, JDK 21, 1 CPU):
```
Tổng số lần GC pause      : 715
  - do Humongous Allocation: 547
Tổng thời gian pause       : 217.5 ms
Pause dài nhất             : 1.06 ms
Heap trước GC (đỉnh)       : 239 MB
Heap sau GC (min / max)    : 1 MB / 2 MB   <- không tăng dần = KHÔNG memory leak
Khoảng thời gian / tần suất: 0.08s -> 10.44s (~69 GC/giây)
```
*(Số liệu mẫu ứng với một request; nếu bạn gửi 5 request, tổng số lần GC sẽ gấp khoảng 5 lần. Số tuyệt đối trên máy bạn sẽ khác, nhưng các đặc điểm sau phải giống: heap sau GC rất thấp, tần suất GC cao, phần lớn GC do Humongous Allocation nếu dùng G1 với region 1 MB.)*

Chụp **terminal có kết quả `gc-summary.sh`** → `06-gc-summary-bad.png`.

Chụp thêm một đoạn log thô để dẫn chứng:
```bash
grep -E "\] GC\([0-9]+\) Pause.*ms$" logs/gc.log | head -5
grep -E "\] GC\([0-9]+\) Pause.*ms$" logs/gc.log | tail -3
```
→ `07-gc-log-lines-bad.png`. Mỗi dòng có dạng:
```
[10.438s][info][gc] GC(1260) Pause Young (Concurrent Start) (G1 Humongous Allocation) 113M->2M(256M) 0.274ms
```
Đọc: *trước GC dùng 113 MB → sau GC còn 2 MB (tổng heap 256 MB), pause 0,274 ms*.

### Bước 8 – (Tùy chọn) Chụp `jstat` theo thời gian thực
Chạy lại tải ở Terminal 2 (`./scripts/load.sh /process 3 1`), đồng thời ở Terminal 3:
```bash
jps -l                          # lấy PID của gc-demo-0.0.1-SNAPSHOT.jar
jstat -gcutil <PID> 1000        # in 1 dòng mỗi giây, Ctrl+C để dừng
```
Quan sát cột **YGC** (số lần young GC) tăng đều theo từng giây và **E** (Eden) liên tục đầy rồi trống → `08-jstat-bad.png`.

### Bước 9 – (Tùy chọn) Chứng minh bản lỗi làm chậm cả hệ thống
Gửi nhiều request đồng thời tới `/process` và đo `/ping`:
```bash
# Terminal 2: 6 request, 3 đồng thời tới bản lỗi (chạy nền)
./scripts/load.sh /process 6 3 &

# Terminal 3: trong lúc đó đo endpoint nhẹ
for i in 1 2 3 4 5; do curl -s -o /dev/null -w "ping: %{time_total}s\n" localhost:8080/ping; sleep 1; done
```
Trên máy ít lõi, thời gian `/ping` tăng rõ rệt (do CPU bị bản lỗi chiếm). Chụp terminal → `09-ping-slow.png`. *(Mức tăng phụ thuộc số lõi CPU của bạn, nên đây chỉ là bằng chứng bổ sung.)*

---

## PHẦN C – THU THẬP BẰNG CHỨNG CHO BẢN ĐÃ SỬA

**Quan trọng:** khởi động lại app để số liệu sạch, không bị lẫn với bản lỗi.

1. Terminal 1: `Ctrl+C` dừng app, rồi chạy lại `./scripts/run.sh` (script tự xóa `logs/gc.log` cũ). Nếu muốn giữ log bản lỗi, copy trước: `cp logs/gc.log evidence/gc-bad.log`.
2. VisualVM: bấm đúp lại tiến trình mới (tiến trình cũ đã biến mất khỏi danh sách).
3. Gọi 1 lần để làm nóng JVM rồi gửi tải:
   ```bash
   curl localhost:8080/process-fixed
   ./scripts/load.sh /process-fixed 200 1
   ```
   Kết quả mỗi request thường chỉ vài chục mili-giây (mẫu đo: ~15–20 ms cho phần xử lý).
4. Chụp:
   - Tab **Monitor** → `10-monitor-fixed.png`: heap gần như **phẳng**, CPU thấp.
   - Tab **Visual GC** → `11-visualgc-fixed.png`: Eden tăng chậm, ít lần GC.
   - `./scripts/gc-summary.sh` → `12-gc-summary-fixed.png`. Với số lượng request nhỏ, kết quả có thể là *"Không có lần GC pause nào"* hoặc chỉ vài lần.
5. Ghi lại thời gian từ output `load.sh` và từ response (`Processing finished in XXms`).

> Mỗi request bản đã sửa chỉ cấp phát ~1 MB, nên GC chỉ xảy ra sau hàng chục request. Nếu muốn thấy vài lần GC, tăng số request: `./scripts/load.sh /process-fixed 2000 4`.

---

## PHẦN D – CHECKLIST ẢNH VÀ SỐ LIỆU

| # | Tên file | Nội dung | Dùng cho mục |
|---|----------|----------|--------------|
| 1 | `01-overview.png` | Overview: cờ JVM, phiên bản Java | Môi trường đo |
| 2 | `02-monitor-bad.png` | Monitor lúc chạy `/process`: heap răng cưa, CPU cao | 2. Bằng chứng |
| 3 | `03-visualgc-bad.png` | Visual GC: Eden đầy/trống liên tục, số GC tăng | 2. Bằng chứng |
| 4 | `04-sampler-cpu-bad.png` | Sampler CPU: hàm ghép chuỗi/copy mảng chiếm Self time | 3. Nguyên nhân gốc |
| 5 | `05-after-gc-no-leak.png` | Sau Perform GC: heap về mức thấp | 1. Không phải leak |
| 6 | `06-gc-summary-bad.png` | Kết quả `gc-summary.sh` bản lỗi | 2. Bằng chứng |
| 7 | `07-gc-log-lines-bad.png` | Vài dòng log GC thô | 2. Bằng chứng |
| 8 | `08-jstat-bad.png` | (tuỳ chọn) `jstat -gcutil` | 2. Bằng chứng |
| 9 | `10-monitor-fixed.png` | Monitor bản sửa: heap phẳng | 4. Sau khi sửa |
| 10 | `12-gc-summary-fixed.png` | `gc-summary.sh` bản sửa | 4. Sau khi sửa |

**Bảng điền số liệu so sánh** (copy vào báo cáo; cột "Mẫu" là số đo của mình để bạn đối chiếu, cột của bạn tự điền):

| Chỉ số | Bản lỗi (của bạn) | Bản sửa (của bạn) | Mẫu bản lỗi | Mẫu bản sửa |
|--------|-------------------|-------------------|-------------|-------------|
| Thời gian xử lý (ms) | | | ~10.400 | ~15–20 |
| Số lần GC | | | 715 / request | 0 |
| Heap sau GC (MB) | | | 1–2 | – |
| Tần suất GC (lần/giây) | | | ~69 | – |
| GC do Humongous Allocation | | | 547 | 0 |

### Thí nghiệm cờ JVM (tùy chọn, để làm phần "đề xuất cờ JVM")
Chạy lại bản lỗi với từng cấu hình, mỗi lần gọi `./scripts/load.sh /process 1 1` rồi `./scripts/gc-summary.sh`:

```bash
# (1) G1 mặc định (region 1 MB)
./scripts/run.sh

# (2) G1 với region 4 MB: loại bỏ các GC do Humongous Allocation
JAVA_OPTS="-XX:G1HeapRegionSize=4m" ./scripts/run.sh

# (3) ParallelGC
GC=Parallel ./scripts/run.sh
```
> `gc-summary.sh` đọc log theo định dạng của `run.sh`, nên dùng script này cho cả ba cấu hình.

Kết quả mẫu của mình (bản lỗi, heap 256 MB): (1) ~10,2 s, 715 GC, 547 do humongous; (2) ~10,2 s, 473 GC, 0 humongous; (3) ~5,9 s. **Kết luận: cờ JVM không thay thế được việc sửa code** (bản sửa chỉ ~15–20 ms và 0 GC).

---

## PHẦN E – CHÈN BẰNG CHỨNG VÀO `markdown.md`

File `markdown.md` (ở thư mục gốc project) đã có sẵn cấu trúc 4 mục theo đề bài. Việc cần làm:
1. Copy ảnh vào thư mục `evidence/`.
2. Ở mục **2.4**, thay hai dòng `> **[Chèn screenshot ... tại đây]**` bằng:
   ```markdown
   ![Monitor – bản lỗi](evidence/02-monitor-bad.png)
   ![Visual GC – bản lỗi](evidence/03-visualgc-bad.png)
   ![Sampler CPU – bản lỗi](evidence/04-sampler-cpu-bad.png)
   ![Sau Perform GC – không leak](evidence/05-after-gc-no-leak.png)
   ```
3. Nếu số liệu trên máy bạn khác với số liệu mẫu trong báo cáo, **thay bằng số của bạn** (số liệu trong báo cáo là của mình, đo trên máy 1 CPU) hoặc ghi rõ "đo trên máy của mình, kết quả xấp xỉ".
4. Thêm ảnh bản đã sửa vào mục **5. Kiểm chứng sau khi sửa**.

---

## Dành cho Windows (nếu không dùng Git Bash)

```bat
REM Terminal 1
scripts\run.bat

REM Terminal 2: gửi 5 request tuần tự tới bản lỗi
for /L %i in (1,1,5) do curl -s -o NUL -w "request %i -> HTTP %{http_code} trong %{time_total}s\n" http://localhost:8080/process

REM bản đã sửa, 200 request
for /L %i in (1,1,200) do curl -s -o NUL http://localhost:8080/process-fixed
```
(Trong file `.bat` đổi `%i` thành `%%i`.)

Tóm tắt log GC bằng PowerShell:
```powershell
$p = Select-String -Path logs\gc.log -Pattern "\] GC\(\d+\) Pause.*ms$"
"Số lần GC pause: " + $p.Count
"Do humongous: " + (Select-String -Path logs\gc.log -Pattern "Humongous Allocation").Count
```

---

## Xử lý sự cố

| Hiện tượng | Cách xử lý |
|------------|-----------|
| VisualVM không thấy tiến trình | Chạy app và VisualVM **cùng một user**; chạy `jps -l` xem app có hiện không (nếu `jps` không có nghĩa là bạn đang dùng JRE, hãy cài JDK); thử đóng/mở lại VisualVM. Có thể bấm chuột phải **Local → Add JMX Connection** nếu cần. |
| Tab **Visual GC** không có | Cài plugin ở mục A2. |
| `permission denied` khi chạy `.sh` | `chmod +x scripts/*.sh` hoặc chạy `bash scripts/run.sh`. |
| `mvn: command not found` | Cài Maven và thêm vào PATH. |
| Cổng 8080 đang bị chiếm | Đổi `server.port` trong `src/main/resources/application.properties`, build lại, và đặt `HOST=http://localhost:<cổng>` khi chạy `load.sh`. |
| Request quá nhanh/quá chậm so với số mẫu | Bình thường, tùy CPU. Bản lỗi chạy đơn luồng nên phụ thuộc tốc độ sao chép bộ nhớ của máy. Nếu quá nhanh để chụp, tăng số request (`load.sh /process 10 1`) hoặc giảm heap (`HEAP=128m ./scripts/run.sh`). |
| `gc-summary.sh` báo "Không có lần GC pause nào" ở bản lỗi | Bạn đang xem nhầm `logs/gc.log` của bản sửa, hoặc chưa gọi `/process`. Chạy lại bản lỗi rồi tóm tắt lại. |
| Log GC bị mất phần đầu | Đừng bỏ `filecount=0` trong cờ `-Xlog`; mặc định JVM xoay vòng và ghi đè file log. |
| App báo `OutOfMemoryError` khi gây tải đồng thời | Heap 256 MB nhỏ với nhiều luồng; tăng `HEAP=512m`. Heap dump được ghi ở `logs/` (mở bằng VisualVM → File → Load). |
