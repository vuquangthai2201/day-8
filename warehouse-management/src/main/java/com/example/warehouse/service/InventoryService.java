package com.example.warehouse.service;

import com.example.warehouse.dto.CreateInventoryRequest;
import com.example.warehouse.dto.InventoryHistoryResponse;
import com.example.warehouse.dto.UpdateInventoryRequest;
import com.example.warehouse.entity.Inventory;
import com.example.warehouse.exception.DuplicateProductException;
import com.example.warehouse.repository.InventoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository repository;
    private final EntityManager entityManager;

    public InventoryService(InventoryRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Inventory create(CreateInventoryRequest req) {
        if (repository.existsById(req.productId())) {
            throw new DuplicateProductException(req.productId());
        }
        return repository.save(new Inventory(req.productId(), req.productName(), req.quantity()));
    }

    @Transactional(readOnly = true)
    public Inventory get(Long productId) {
        return findOrThrow(productId);
    }

    /** Nhap hang - PESSIMISTIC LOCKING (SELECT ... FOR UPDATE). */
    @Transactional
    public Inventory stockIn(Long productId, int amount) {
        Inventory inv = repository.findByIdForUpdate(productId)
                .orElseThrow(() -> notFound(productId));
        inv.setQuantity(inv.getQuantity() + amount);   // dirty checking -> UPDATE khi commit
        return inv;
    }

    /** Sua thong tin - OPTIMISTIC LOCKING (@Version). */
    @Transactional
    public Inventory update(Long productId, UpdateInventoryRequest req) {
        Inventory inv = findOrThrow(productId);

        // Client dang giu version cu => da co nguoi khac sua truoc.
        if (!inv.getVersion().equals(req.version())) {
            throw new ObjectOptimisticLockingFailureException(Inventory.class, productId);
        }
        inv.setProductName(req.productName());

        // flush de: (1) Hibernate chay "UPDATE ... WHERE version = ?" ngay trong method
        // (bat duoc race giua luc kiem tra va luc ghi), (2) version moi co san de tra ve.
        return repository.saveAndFlush(inv);
    }

    /** Lich su thay doi (Hibernate Envers). */
    @Transactional(readOnly = true)
    public List<InventoryHistoryResponse> history(Long productId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(Inventory.class, false, true)
                .add(AuditEntity.id().eq(productId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        if (rows.isEmpty() && !repository.existsById(productId)) {
            throw notFound(productId);
        }

        List<InventoryHistoryResponse> result = new ArrayList<>();
        int previousQty = 0;
        for (Object[] row : rows) {
            Inventory snapshot = (Inventory) row[0];
            DefaultRevisionEntity rev = (DefaultRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];

            int currentQty = (type == RevisionType.DEL) ? 0 : snapshot.getQuantity();
            result.add(new InventoryHistoryResponse(
                    rev.getId(),
                    type.name(),
                    LocalDateTime.ofInstant(rev.getRevisionDate().toInstant(), ZoneId.systemDefault()),
                    snapshot.getProductName(),
                    currentQty,
                    currentQty - previousQty));
            previousQty = currentQty;
        }
        return result;
    }

    private Inventory findOrThrow(Long productId) {
        return repository.findById(productId).orElseThrow(() -> notFound(productId));
    }

    private EntityNotFoundException notFound(Long productId) {
        return new EntityNotFoundException("Product not found: " + productId);
    }
}
