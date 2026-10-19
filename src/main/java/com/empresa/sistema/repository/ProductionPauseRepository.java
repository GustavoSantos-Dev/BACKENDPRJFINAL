package com.empresa.sistema.repository;

import com.empresa.sistema.entity.ProductionPause;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionPauseRepository extends JpaRepository<ProductionPause, Long> {
  java.util.List<ProductionPause> findByBatchIdOrderByPausedAtAsc(Long batchId);

  java.util.Optional<ProductionPause> findByBatchIdAndResumedAtIsNull(Long batchId);
}
