package com.example.flashsale.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final JdbcTemplate primary;
    private final JdbcTemplate replica;

    public ProductRepository(JdbcTemplate primary, @Qualifier("readJdbc") JdbcTemplate replica) {
        this.primary = primary;
        this.replica = replica;
    }

    public List<Long> allIds() {
        return primary.query("select id from product", (rs, i) -> rs.getLong(1));
    }

    public Optional<BigDecimal> price(long id) {
        return primary.query("select price from product where id = ?", (rs, i) -> rs.getBigDecimal(1), id)
                .stream().findFirst();
    }

    public Optional<Integer> stock(long id) {
        return primary.query("select stock from product where id = ?", (rs, i) -> rs.getInt(1), id)
                .stream().findFirst();
    }

    /** Đọc từ replica: có thể trễ, chỉ dùng để hiển thị. */
    public Optional<Integer> stockFromReplica(long id) {
        return replica.query("select stock from product where id = ?", (rs, i) -> rs.getInt(1), id)
                .stream().findFirst();
    }

    /**
     * Chốt chặn oversell cuối cùng: trừ có điều kiện, nguyên tử trong một câu lệnh.
     * Trả về 0 nếu không đủ hàng.
     */
    public int tryDecrement(long id, int qty) {
        return primary.update("update product set stock = stock - ? where id = ? and stock >= ?", qty, id, qty);
    }

    public void increment(long id, int qty) {
        primary.update("update product set stock = stock + ? where id = ?", qty, id);
    }

    public int setStock(long id, int stock) {
        return primary.update("update product set stock = ? where id = ?", stock, id);
    }
}
