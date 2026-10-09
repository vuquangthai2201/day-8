package com.example.warehouse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.envers.Audited;

/**
 * - @Audited       : Envers luu moi thay doi vao bang inventory_aud.
 * - @Version       : Optimistic Locking cho API sua thong tin san pham.
 * - @DynamicUpdate : UPDATE chi chua cac cot thuc su thay doi. Rat quan trong:
 *                    neu khong, PUT (doc quantity cu) co the ghi de so luong vua
 *                    duoc stock-in (lost update), vi quantity khong duoc bao ve boi version.
 * - @OptimisticLock(excluded = true) tren quantity: nhap hang da duoc bao ve boi
 *                    pessimistic lock nen khong can tang version (tranh PUT bi 409 oan).
 */
@Entity
@Audited
@DynamicUpdate
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @OptimisticLock(excluded = true)
    @Column(nullable = false)
    private int quantity;

    @Version
    private Long version;

    protected Inventory() {
    }

    public Inventory(Long productId, String productName, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getProductId() { return productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public Long getVersion() { return version; }
}
