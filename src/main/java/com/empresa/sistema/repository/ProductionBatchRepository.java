package com.empresa.sistema.repository;

import com.empresa.sistema.entity.ProductionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select b.device.id from ProductionBatch b where b.id=:id")
  java.util.Optional<String> deviceIdForBatch(
      @org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select b from ProductionBatch b where b.id=:id")
  java.util.Optional<ProductionBatch> findLockedById(
      @org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select b from ProductionBatch b where b.operationId=:operationId")
  java.util.Optional<ProductionBatch> findLockedByOperationId(
      @org.springframework.data.repository.query.Param("operationId") String operationId);

  boolean existsByDeviceIdAndStatusInAndIdNot(
      String deviceId,
      java.util.Collection<com.empresa.sistema.entity.BatchStatus> statuses,
      Long id);

  java.util.List<ProductionBatch> findByStatusIn(
      java.util.Collection<com.empresa.sistema.entity.BatchStatus> statuses);
}
