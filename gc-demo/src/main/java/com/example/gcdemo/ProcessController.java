package com.example.gcdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProcessController {

    private static final int ITERATIONS = 150_000;

    /**
     * BẢN LỖI (đề bài): nối chuỗi bằng += trong vòng lặp.
     * Mỗi vòng tạo một String mới và copy toàn bộ chuỗi cũ -> O(n^2), ~67 GB rác cho một request.
     */
    @GetMapping("/process")
    public String processData() {
        long startTime = System.currentTimeMillis();
        String result = "";
        for (int i = 0; i < ITERATIONS; i++) {
            result += " " + i;
        }
        long endTime = System.currentTimeMillis();
        return "Processing finished in " + (endTime - startTime) + "ms. Result length: " + result.length();
    }

    /**
     * BẢN ĐÃ SỬA: StringBuilder ghi thêm vào cuối mảng -> O(n), ~1 MB bộ nhớ.
     * 938.890 là độ dài kết quả; cấp sẵn 1.000.000 để tránh resize.
     */
    @GetMapping("/process-fixed")
    public String processDataFixed() {
        long start = System.nanoTime();
        StringBuilder sb = new StringBuilder(1_000_000);
        for (int i = 0; i < ITERATIONS; i++) {
            sb.append(' ').append(i);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return "Processing finished in " + elapsedMs + "ms. Result length: " + sb.length();
    }

    /** Endpoint nhẹ để kiểm tra app đã lên và để đo "độ chậm lan sang endpoint khác". */
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
