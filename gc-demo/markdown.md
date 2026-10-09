# Bài 13 – Phân tích hiệu năng endpoint `/process`

## Tóm tắt

| Mục | Kết luận |
|-----|----------|
| Loại vấn đề | **High allocation rate / High GC churn** kèm **CPU lãng phí do độ phức tạp O(n²)**. **Không phải memory leak.** |
| Nguyên nhân gốc | Nối chuỗi bằng `result += ...` trong vòng lặp 150.000 lần: mỗi vòng tạo một `String` mới và sao chép toàn bộ nội dung cũ. |
| Mức độ (đo thật) | ~10,2 giây cho một request, ước tính ~67 GB bộ nhớ được cấp phát và sao chép, 715 lần GC chỉ trong ~10 giây. |
| Cách sửa | Dùng `StringBuilder` (hoặc không dựng chuỗi lớn trong bộ nhớ). Thời gian giảm còn ~15–20 ms, **không còn lần GC nào**. |
| Cờ JVM | Chỉ giảm nhẹ triệu chứng, không sửa được nguyên nhân gốc (xem mục 4). |

---

## 0. Môi trường đo và lưu ý trung thực

- Số liệu bên dưới là **số liệu đo thật**, lấy từ một chương trình Java độc lập (`Bad.java`) chứa **nguyên văn vòng lặp của endpoint**, không phải từ ứng dụng Spring Boot. Logic cấp phát bộ nhớ là như nhau, nhưng khi chạy trong Tomcat bạn sẽ có thêm overhead của framework.
- Máy đo: JDK 21 (OpenJDK 21.0.10), **1 vCPU**, 4 GB RAM, heap cố định `-Xms256m -Xmx256m`, G1 GC. Thời gian tuyệt đối sẽ khác trên máy của bạn; **tỉ lệ và hình dạng đồ thị** mới là điều đáng chú ý.
- Mình **không có giao diện đồ họa nên không chụp được screenshot VisualVM**. Mục 2.4 hướng dẫn cách chụp và có chỗ để bạn chèn ảnh. Log GC và số liệu ở mục 2.1–2.3 là thật.

---

## 1. Vấn đề tìm thấy là gì?

Endpoint `/process` có ba biểu hiện:

1. **High GC churn (tỉ lệ cấp phát cực cao).** Mỗi vòng lặp cấp phát một mảng byte mới có kích thước bằng toàn bộ chuỗi hiện tại (trung bình ~437 KB). Tổng cộng khoảng **67 GB** rác chỉ cho một request, tương đương ~6,4 GB/giây.
2. **CPU bị đốt vào việc sao chép bộ nhớ.** Độ phức tạp là O(n²). Chuỗi cuối chỉ dài 938.890 ký tự nhưng tổng dữ liệu bị copy gấp **~71.500 lần** con số đó.
3. **Humongous allocation (đặc thù của G1).** Với region 1 MB, mọi đối tượng ≥ 512 KB bị coi là *humongous*. Chuỗi vượt ngưỡng này từ vòng lặp thứ ~89.232 trở đi, và **547/715 lần GC (76%) được kích hoạt trực tiếp bởi "G1 Humongous Allocation"**.

**Đây không phải memory leak.** Sau mỗi lần GC, heap quay về ~1–2 MB (xem bằng chứng bên dưới), và biến `result` là biến cục bộ nên không có gì giữ tham chiếu lâu dài. Heap trông như hình răng cưa, không dốc lên.

---

## 2. Bằng chứng

### 2.1. Thời gian chạy

Chạy cùng một đoạn code bằng `java -Xms256m -Xmx256m -XX:+UseG1GC Bad`:

```
Processing finished in 10413ms. Result length: 938890
```

Bản đã sửa (`Good`), chạy 4 lần:

```
Processing finished in 16ms. Result length: 938890
Processing finished in 20ms. Result length: 938890
Processing finished in 15ms. Result length: 938890
Processing finished in 18ms. Result length: 938890
```

Kết quả (`Result length: 938890`) giống hệt nhau, nên bản sửa không làm thay đổi hành vi.

### 2.2. Log GC (bản lỗi)

Bật log bằng:

```bash
java -Xms256m -Xmx256m -XX:+UseG1GC \
  "-Xlog:gc*:file=gc-bad.log:time,uptime,level,tags:filecount=0" Bad
```

Trích log thật (các dòng đầu, một dòng ở giữa và dòng cuối):

```
[0.076s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 13M->1M(256M) 1.064ms
[0.085s][info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 16M->1M(256M) 0.687ms
[0.195s][info][gc] GC(2) Pause Young (Normal) (G1 Evacuation Pause) 153M->1M(256M) 1.050ms
...
[5.813s][info][gc] GC(600) Pause Young (Concurrent Start) (G1 Humongous Allocation) 113M->2M(256M) 0.307ms
...
[10.438s][info][gc] GC(1260) Pause Young (Concurrent Start) (G1 Humongous Allocation) 113M->2M(256M) 0.274ms
```

Cách đọc: `153M->1M(256M)` nghĩa là trước GC heap dùng 153 MB, sau GC chỉ còn 1 MB (tổng heap 256 MB).

Thống kê tổng hợp từ toàn bộ log:

| Chỉ số | Giá trị |
|--------|---------|
| Tổng số lần GC pause | **715** trong ~10,4 giây (~69 lần/giây) |
| Trong đó do `G1 Humongous Allocation` | **547** (76%) |
| Trong đó `G1 Evacuation Pause` thường | 168 |
| Heap dùng **sau** GC | **1–2 MB** (không tăng, loại trừ memory leak) |
| Heap dùng **trước** GC (đỉnh) | 239 MB |
| Pause dài nhất | ~1,06 ms |
| Tổng thời gian pause | ~190–220 ms (~2% thời gian chạy) |
| Kích thước region G1 | 1 MB, ngưỡng humongous = 512 KB |

**Nhận xét quan trọng:** thời gian pause của GC rất nhỏ (~2%). Nghĩa là **GC không phải là thứ làm request chậm**; GC chỉ là *triệu chứng* của việc cấp phát điên cuồng. Thời gian thực sự bị tiêu vào việc `memcpy` hàng chục GB trong chính vòng lặp. Vì vậy chỉnh GC sẽ không cứu được endpoint này.

### 2.3. Con số lý thuyết (tính từ code)

Độ dài chuỗi sau vòng lặp thứ `i` là Σ(1 + số chữ số của k) với k từ 0 đến i.

| Đại lượng | Giá trị |
|-----------|---------|
| Độ dài cuối cùng | 938.890 ký tự (≈ 0,9 MB với Latin-1) |
| Tổng byte được cấp phát và copy qua 150.000 vòng | **≈ 67,1 GB** (62,5 GiB) |
| Trung bình mỗi vòng | ≈ 437 KB |
| Tỉ lệ so với kích thước kết quả cuối | ≈ 71.500 lần |
| Vòng lặp mà chuỗi vượt 512 KB (ngưỡng humongous, region 1 MB) | ≈ vòng 89.232 |
| Trên JDK 8 (chuỗi UTF-16, 2 byte/ký tự) | ≈ 134 GB, và còn bị copy gấp đôi do `StringBuilder` ngầm (xem mục 3) |

### 2.4. Cách lấy screenshot VisualVM (để chèn vào báo cáo)

1. Chạy ứng dụng với heap nhỏ để đồ thị rõ: `java -Xms256m -Xmx256m -jar app.jar`.
2. Mở VisualVM → chọn process → tab **Monitor**. Cài plugin **Visual GC** nếu muốn xem từng vùng heap.
3. Gọi `curl localhost:8080/process` vài lần liên tiếp, chụp lại.

Những gì bạn sẽ thấy nếu khớp với số liệu trên:

- Biểu đồ **Heap**: hình **răng cưa** dày đặc, đỉnh vài trăm MB, đáy luôn quay về gần 0 (đây là bằng chứng *không leak*).
- **CPU**: một core ở mức ~100% trong suốt thời gian request.
- Tab Sampler/Profiler (CPU): thời gian dồn vào `String.concat` / `StringConcatHelper` / `Arrays.copyOf`.

> **[Chèn screenshot Monitor tại đây]**
>
> **[Chèn screenshot Visual GC tại đây]**

Cách thay thế không cần giao diện:

```bash
jstat -gcutil <pid> 500          # theo dõi E, O, YGC, GCT mỗi 500 ms
jcmd <pid> GC.class_histogram    # nhiều byte[] kích thước lớn
jcmd <pid> JFR.start duration=30s filename=process.jfr   # xem Allocation by Class/Site trong JMC
```

### 2.5. So sánh các cấu hình JVM trên code lỗi

Cùng heap 256 MB, cùng code lỗi, đo thật:

| Cấu hình | Thời gian | Số lần GC | GC do humongous | Tổng pause |
|----------|-----------|-----------|-----------------|------------|
| G1, region mặc định (1 MB) | 10.217 ms | 715 | 547 | ~187 ms |
| G1 + `-XX:G1HeapRegionSize=4m` | 10.185 ms | 473 | **0** | ~152 ms |
| ParallelGC | **5.912 ms** | 776 | 0 | ~93 ms |
| **G1, code đã sửa (StringBuilder)** | **~15–20 ms** | **0** | 0 | 0 |

Bài học: đổi cờ JVM chỉ giảm GC humongous hoặc giảm thời gian từ ~10 giây xuống ~6 giây. **Sửa code đưa thời gian về mức mili-giây (nhanh hơn ~500 lần) và triệt tiêu hoàn toàn GC.**

---

## 3. Nguyên nhân gốc rễ trong code

```java
String result = "";
for (int i = 0; i < 150_000; i++) {
    result += " " + i;     // <-- gốc rễ
}
```

`String` là bất biến (immutable). Câu lệnh `result += " " + i` thực chất là `result = result + " " + i`, nghĩa là mỗi vòng lặp:

1. Cấp phát một mảng `byte[]` mới có kích thước `len(result) + len(" " + i)`.
2. **Sao chép toàn bộ** `result` cũ sang mảng mới.
3. Bỏ mảng cũ cho GC thu dọn.

Vòng lặp thứ `i` tốn O(i) thời gian và bộ nhớ, nên tổng là **O(n²)**: 150.000 vòng sao chép tổng cộng ~67 GB để tạo ra chuỗi chỉ ~0,9 MB.

Chi tiết theo phiên bản JDK:

- **JDK 9+**: trình biên dịch dùng `invokedynamic` (`StringConcatFactory`), mỗi vòng cấp phát **một** chuỗi mới có độ dài đầy đủ. Vẫn là O(n²).
- **JDK 8**: trình biên dịch sinh `new StringBuilder().append(result).append(" ").append(i).toString()` **trong mỗi vòng lặp**. Chuỗi bị copy **hai lần** mỗi vòng (một lần vào builder, một lần ở `toString()`), và mỗi ký tự chiếm 2 byte, nên còn tệ hơn.

Hệ quả phụ:

- Thread của Tomcat bị chiếm **hơn 10 giây cho một request** (trên máy 1 core). Với thread pool mặc định 200 thread, chỉ cần vài chục request đồng thời là CPU bão hòa, GC chạy liên tục và các endpoint khác bị chậm theo.
- Với G1, các chuỗi lớn rơi vào vùng *humongous* (mục 1), làm tăng tần suất GC.

---

## 4. Cách sửa và cờ JVM đề xuất

### 4.1. Sửa code (giải pháp chính)

```java
@GetMapping("/process")
public String processData() {
    long start = System.nanoTime();

    // 938.890 là độ dài kết quả; cấp sẵn dung lượng để tránh resize
    StringBuilder sb = new StringBuilder(1_000_000);
    for (int i = 0; i < 150_000; i++) {
        sb.append(' ').append(i);
    }

    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    return "Processing finished in " + elapsedMs + "ms. Result length: " + sb.length();
}
```

Vì sao nhanh: `StringBuilder` là mảng có thể thay đổi, mỗi vòng lặp chỉ **ghi thêm vào cuối** (O(1) khấu hao). Tổng công việc là O(n), bộ nhớ cấp phát ≈ 1 MB thay vì 67 GB.

Các lựa chọn khác:

| Cách | Khi nào dùng |
|------|--------------|
| `String.join(" ", ...)` / `Collectors.joining(" ")` | Khi dữ liệu đã nằm trong collection/stream. |
| `StringBuilder` đã pre-size | Vòng lặp tuần tự đơn giản như bài này. |
| **Không dựng chuỗi lớn trong bộ nhớ** | Nếu chuỗi dành cho response: ghi thẳng ra `OutputStream`/`Writer` hoặc dùng `StreamingResponseBody`, để mỗi request không giữ cả ~1 MB trong heap. |
| Nếu chỉ cần độ dài | Tính toán trực tiếp bằng công thức, không cần tạo chuỗi. |

Ngoài ra, đoạn code gốc trong đề có hai lỗi cú pháp: `result. Length()` phải là `result.length()` (Java phân biệt hoa thường), và thiếu dấu `}` đóng method.

Hướng phòng ngừa: bật kiểm tra tĩnh (SonarQube/IntelliJ inspection "String concatenation in loop"), và đặt giới hạn thời gian/độ lớn cho các endpoint xử lý dữ liệu.

### 4.2. Cờ JVM đề xuất

Các cờ này **hỗ trợ giám sát và giảm nhẹ triệu chứng**, không thay thế việc sửa code (xem bảng 2.5).

```bash
java \
  -Xms512m -Xmx512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:G1HeapRegionSize=4m \
  "-Xlog:gc*,gc+humongous=debug:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m" \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=logs/ \
  -jar app.jar
```

| Cờ | Tác dụng |
|----|----------|
| `-Xms` = `-Xmx` | Heap cố định, tránh GC phải co giãn heap và tránh đo đạc bị nhiễu. |
| `-XX:+UseG1GC` | Collector mặc định từ JDK 9, cân bằng giữa độ trễ và thông lượng. |
| `-XX:MaxGCPauseMillis=200` | Mục tiêu thời gian pause của G1 (là mục tiêu, không phải cam kết). |
| `-XX:G1HeapRegionSize=4m` | Tăng ngưỡng humongous lên 2 MB để các chuỗi/mảng cỡ ~1 MB không bị coi là humongous. Đo thật: số GC do humongous giảm từ 547 xuống 0. Giá trị hợp lệ là lũy thừa của 2 từ 1m đến 32m. |
| `-Xlog:gc*,gc+humongous=debug:...` | Log GC có xoay vòng file. **Lưu ý**: mặc định JDK xoay vòng log và ghi đè file cũ, nên khi phân tích hãy chỉ định `filecount`/`filesize` hợp lý (hoặc `filecount=0` khi chạy thử), nếu không bạn sẽ chỉ thấy phần cuối. |
| `-XX:+HeapDumpOnOutOfMemoryError` | Tự động dump heap khi hết bộ nhớ, để phân tích bằng Eclipse MAT/VisualVM. |

Một số cờ khác nên cân nhắc tùy môi trường:

- **Container (Docker/K8s):** dùng `-XX:MaxRAMPercentage=75.0` thay cho `-Xmx` cố định, và `-XX:+ExitOnOutOfMemoryError` để orchestrator tự khởi động lại pod.
- **Ưu tiên thông lượng, chấp nhận pause dài hơn:** `-XX:+UseParallelGC` (trong thí nghiệm trên cho kết quả nhanh hơn G1 ở code lỗi, nhưng vẫn mất ~6 giây vì nguyên nhân gốc chưa được sửa).
- **Độ trễ cực thấp, heap lớn:** `-XX:+UseZGC` (JDK 21 hỗ trợ ZGC thế hệ: `-XX:+ZGenerational`).
- `-XX:+UseStringDeduplication` (chỉ G1) **không giúp được ở đây**, vì các chuỗi trung gian đều khác nhau và có vòng đời cực ngắn.

---

## 5. Kiểm chứng sau khi sửa

| Chỉ số | Trước | Sau |
|--------|-------|-----|
| Thời gian xử lý | ~10.400 ms | **~15–20 ms** |
| Số lần GC trong lúc xử lý | 715 | **0** |
| Bộ nhớ cấp phát cho request | ~67 GB | **~1 MB** |
| Kết quả (`Result length`) | 938890 | 938890 (không đổi) |

Để kiểm chứng trên ứng dụng Spring Boot thật, chạy tải rồi so sánh log GC và đồ thị VisualVM trước/sau:

```bash
# cần cài 'hey' hoặc dùng Apache Bench
hey -n 200 -c 20 http://localhost:8080/process
```

Bản lỗi sẽ cho đồ thị heap răng cưa dày và CPU kịch trần; bản đã sửa cho đường heap gần như phẳng và độ trễ ở mức mili-giây.

---

## Phụ lục: mã nguồn dùng để đo

`Bad.java` (nguyên văn vòng lặp của đề bài):

```java
public class Bad {
    public static void main(String[] a) {
        long startTime = System.currentTimeMillis();
        String result = "";
        for (int i = 0; i < 150_000; i++) {
            result += " " + i;
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Processing finished in " + (endTime - startTime)
                + "ms. Result length: " + result.length());
    }
}
```

`Good.java`:

```java
public class Good {
    public static void main(String[] a) {
        long start = System.nanoTime();
        StringBuilder sb = new StringBuilder(1_000_000);
        for (int i = 0; i < 150_000; i++) {
            sb.append(' ').append(i);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Processing finished in " + ms + "ms. Result length: " + sb.length());
    }
}
```
