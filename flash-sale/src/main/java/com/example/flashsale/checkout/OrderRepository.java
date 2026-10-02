package com.example.flashsale.checkout;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private static final RowMapper<Order> MAPPER = (rs, i) -> new Order(
            rs.getObject("id", UUID.class), rs.getString("user_id"), rs.getLong("product_id"),
            rs.getInt("qty"), rs.getBigDecimal("amount"), rs.getString("status"),
            rs.getString("idempotency_key"));

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(Order o) {
        jdbc.update("""
                insert into orders (id, user_id, product_id, qty, amount, status, idempotency_key)
                values (?, ?, ?, ?, ?, ?, ?)
                """, o.id(), o.userId(), o.productId(), o.qty(), o.amount(), o.status(), o.idempotencyKey());
    }

    public Optional<Order> findById(UUID id) {
        return jdbc.query("select * from orders where id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Order> findByIdempotencyKey(String key) {
        return jdbc.query("select * from orders where idempotency_key = ?", MAPPER, key).stream().findFirst();
    }

    /** Chuyển trạng thái có điều kiện. Chỉ một bên thắng (1 dòng), bên còn lại nhận 0. */
    public int markIfPending(UUID id, String status) {
        return jdbc.update("update orders set status = ?, updated_at = now() where id = ? and status = 'PENDING'",
                status, id);
    }

    public List<Order> findStalePending(int olderThanSeconds, int limit) {
        return jdbc.query("""
                select * from orders
                where status = 'PENDING' and created_at < now() - (? * interval '1 second')
                order by created_at limit ?
                """, MAPPER, olderThanSeconds, limit);
    }

    public int countByStatus(String status) {
        Integer n = jdbc.queryForObject("select count(*) from orders where status = ?", Integer.class, status);
        return n == null ? 0 : n;
    }
}
