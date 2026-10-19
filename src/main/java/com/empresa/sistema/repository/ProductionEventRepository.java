package com.empresa.sistema.repository;

import com.empresa.sistema.entity.ProductionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionEventRepository extends JpaRepository<ProductionEvent, java.util.UUID> {
  org.springframework.data.domain.Page<ProductionEvent> findByBatchIdOrderByReceivedAtDesc(
      Long batchId, org.springframework.data.domain.Pageable p);
}
