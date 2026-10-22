package com.example.perfproject.service;

import com.example.perfproject.dto.AuthorDto;
import com.example.perfproject.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;

    /** TRƯỚC khi sửa: lỗi N+1. 1 query lấy author + N query lấy books của từng author. */
    @Transactional(readOnly = true)
    public List<AuthorDto> getAuthorsSlow() {
        return authorRepository.findAll().stream()
                .map(a -> new AuthorDto(a.getId(), a.getName(), a.getBooks().size()))
                .toList();
    }

    /** SAU khi sửa: JOIN FETCH, đúng 1 query. */
    @Transactional(readOnly = true)
    public List<AuthorDto> getAuthorsFast() {
        return authorRepository.findAllWithBooks().stream()
                .map(a -> new AuthorDto(a.getId(), a.getName(), a.getBooks().size()))
                .toList();
    }
}
