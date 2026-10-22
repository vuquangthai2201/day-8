package com.example.perfproject.service;

import com.example.perfproject.dto.BookDto;
import com.example.perfproject.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;

    /** TRƯỚC: lấy sách theo năm rồi đọc tên tác giả -> mỗi tác giả khác nhau phát sinh thêm 1 query. */
    @Transactional(readOnly = true)
    public List<BookDto> getBooksByYearSlow(int year) {
        return bookRepository.findByYearLazy(year).stream()
                .map(b -> new BookDto(b.getId(), b.getTitle(), b.getPublicationYear(), b.getAuthor().getName()))
                .toList();
    }

    /** SAU: EntityGraph nạp author cùng lúc -> 1 query. */
    @Transactional(readOnly = true)
    public List<BookDto> getBooksByYearFast(int year) {
        return bookRepository.findByPublicationYear(year).stream()
                .map(b -> new BookDto(b.getId(), b.getTitle(), b.getPublicationYear(), b.getAuthor().getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countByYear(int year) {
        return bookRepository.countByPublicationYear(year);
    }
}
