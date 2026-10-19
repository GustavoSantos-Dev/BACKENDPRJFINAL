package com.empresa.sistema.repository;

import com.empresa.sistema.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, String> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select d from Device d where d.id=:id")
  java.util.Optional<Device> findLockedById(
      @org.springframework.data.repository.query.Param("id") String id);
}
