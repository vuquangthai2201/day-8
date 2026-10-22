package com.example.perfproject.controller;

import com.example.perfproject.dto.AuthorDto;
import com.example.perfproject.dto.BookDto;
import com.example.perfproject.service.AuthorService;
import com.example.perfproject.service.BenchmarkService;
import com.example.perfproject.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PerfController {

    private final AuthorService authorService;
    private final BookService bookService;
    private final BenchmarkService benchmarkService;

    /** Phiên bản CHƯA tối ưu (N+1). */
    @GetMapping("/authors/slow")
    public List<AuthorDto> authorsSlow() {
        return authorService.getAuthorsSlow();
    }

    /** Phiên bản ĐÃ tối ưu (JOIN FETCH). */
    @GetMapping("/authors/fast")
    public List<AuthorDto> authorsFast() {
        return authorService.getAuthorsFast();
    }

    @GetMapping("/books/slow")
    public List<BookDto> booksSlow(@RequestParam(defaultValue = "2000") int year) {
        return bookService.getBooksByYearSlow(year);
    }

    @GetMapping("/books/fast")
    public List<BookDto> booksFast(@RequestParam(defaultValue = "2000") int year) {
        return bookService.getBooksByYearFast(year);
    }

    /** Chạy toàn bộ kịch bản trước/sau và trả về số SQL + thời gian. */
    @GetMapping("/benchmark")
    public Map<String, Object> benchmark() {
        return benchmarkService.run();
    }
}
