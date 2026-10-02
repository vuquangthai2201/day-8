package com.example.flashsale.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hai đường kết nối:
 *  - jdbcTemplate (primary): mọi thao tác ghi và đọc cần nhất quán (checkout).
 *  - readJdbc (replica): chỉ dùng cho listing fallback, chấp nhận trễ.
 * Nếu app.replica.url rỗng thì readJdbc dùng chung primary.
 */
@Configuration
public class JdbcConfig {

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource primary) {
        return new JdbcTemplate(primary);
    }

    @Bean(name = "readJdbc")
    public JdbcTemplate readJdbc(DataSource primary,
                                 @Value("${app.replica.url:}") String url,
                                 @Value("${app.replica.username:}") String username,
                                 @Value("${app.replica.password:}") String password) {
        if (url == null || url.isBlank()) {
            return new JdbcTemplate(primary);
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(20);
        ds.setConnectionTimeout(250);
        return new JdbcTemplate(ds);
    }
}
