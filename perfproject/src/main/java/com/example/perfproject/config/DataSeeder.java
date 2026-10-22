package com.example.perfproject.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Nạp dữ liệu mẫu bằng JDBC batch (nhanh), ngoài phạm vi đo của benchmark. */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final int authors;
    private final int booksPerAuthor;

    public DataSeeder(JdbcTemplate jdbc,
                      @Value("${app.seed.authors:1000}") int authors,
                      @Value("${app.seed.books-per-author:100}") int booksPerAuthor) {
        this.jdbc = jdbc;
        this.authors = authors;
        this.booksPerAuthor = booksPerAuthor;
    }

    @Override
    public void run(String... args) {
        long start = System.currentTimeMillis();

        List<Object[]> authorRows = new ArrayList<>();
        for (int i = 1; i <= authors; i++) {
            authorRows.add(new Object[]{"Author " + i});
        }
        jdbc.batchUpdate("INSERT INTO author(name) VALUES (?)", authorRows);

        // Lấy id thật của author (không giả định id bắt đầu từ 1)
        List<Long> authorIds = jdbc.queryForList("SELECT id FROM author ORDER BY id", Long.class);

        Random random = new Random(42);
        List<Object[]> bookRows = new ArrayList<>(authors * booksPerAuthor);
        for (Long authorId : authorIds) {
            for (int j = 1; j <= booksPerAuthor; j++) {
                int year = 1950 + random.nextInt(76); // 1950..2025
                bookRows.add(new Object[]{"Book " + authorId + "-" + j, year, authorId});
            }
        }
        jdbc.batchUpdate("INSERT INTO book(title, publication_year, author_id) VALUES (?, ?, ?)", bookRows);

        log.info("Seeded {} authors and {} books in {} ms",
                authorIds.size(), bookRows.size(), System.currentTimeMillis() - start);
    }
}
