package com.example.perfproject.service;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tự đo "trước/sau": số câu SQL (Hibernate Statistics) và thời gian trung bình.
 * Mỗi kịch bản: 1 lần warm-up (không tính) + RUNS lần đo, lấy trung bình.
 */
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private static final int RUNS = 5;
    private static final int YEAR = 2000;
    private static final String INDEX_NAME = "idx_book_publication_year";

    private final AuthorService authorService;
    private final BookService bookService;
    private final JdbcTemplate jdbc;
    private final EntityManagerFactory emf;

    public Map<String, Object> run() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", Map.of(
                "authors", jdbc.queryForObject("SELECT COUNT(*) FROM author", Long.class),
                "books", jdbc.queryForObject("SELECT COUNT(*) FROM book", Long.class),
                "runsPerScenario", RUNS));

        // 1) N+1 ở Author -> Books
        result.put("1_authorsWithBooks_before_N+1", measure(stats, () -> authorService.getAuthorsSlow()));
        result.put("1_authorsWithBooks_after_joinFetch", measure(stats, () -> authorService.getAuthorsFast()));

        // 2) N+1 ở Book -> Author
        result.put("2_booksByYear_before_N+1", measure(stats, () -> bookService.getBooksByYearSlow(YEAR)));
        result.put("2_booksByYear_after_entityGraph", measure(stats, () -> bookService.getBooksByYearFast(YEAR)));

        // 3) Index: đo lúc có index, drop index đo lại, rồi tạo lại index
        result.put("3_countByYear_after_withIndex", measure(stats, () -> bookService.countByYear(YEAR)));
        try {
            jdbc.execute("DROP INDEX IF EXISTS " + INDEX_NAME);
            result.put("3_countByYear_before_noIndex", measure(stats, () -> bookService.countByYear(YEAR)));
        } finally {
            jdbc.execute("CREATE INDEX IF NOT EXISTS " + INDEX_NAME + " ON book(publication_year)");
        }
        return result;
    }

    private Map<String, Object> measure(Statistics stats, Supplier<?> task) {
        task.get(); // warm-up

        List<Long> times = new ArrayList<>();
        long queries = 0;
        for (int i = 0; i < RUNS; i++) {
            stats.clear();
            long t0 = System.nanoTime();
            task.get();
            times.add((System.nanoTime() - t0) / 1_000_000);
            queries = stats.getPrepareStatementCount();
        }
        double avg = times.stream().mapToLong(Long::longValue).average().orElse(0);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sqlStatements", queries);
        m.put("avgMs", Math.round(avg * 10) / 10.0);
        m.put("allRunsMs", times);
        return m;
    }
}
