package com.example.warehouse.repository;

import com.example.warehouse.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /** SELECT ... FOR UPDATE: cac giao dich nhap hang khac phai cho den khi giao dich nay ket thuc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") Long id);
}
