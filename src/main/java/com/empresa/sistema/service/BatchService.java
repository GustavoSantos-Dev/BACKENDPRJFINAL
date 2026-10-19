package com.empresa.sistema.service;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.entity.*;
import com.empresa.sistema.exception.ApiException;
import com.empresa.sistema.repository.*;
import com.empresa.sistema.security.CurrentUser;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BatchService {
  private final ProductionBatchRepository batches;
  private final ScrewTypeRepository screws;
  private final DeviceRepository devices;
  private final ProductionPauseRepository pauses;
  private final ProductionEventRepository events;
  private final CurrentUser current;

  public BatchService(
      ProductionBatchRepository batches,
      ScrewTypeRepository screws,
      DeviceRepository devices,
      ProductionPauseRepository pauses,
      ProductionEventRepository events,
      CurrentUser current) {
    this.batches = batches;
    this.screws = screws;
    this.devices = devices;
    this.pauses = pauses;
    this.events = events;
    this.current = current;
  }

  public Responses.BatchView create(Requests.BatchCreate r) {
    ScrewType s =
        screws
            .findById(r.screwTypeId())
            .orElseThrow(() -> ApiException.missing("Tipo de parafuso"));
    Device d =
        devices.findById(r.deviceId()).orElseThrow(() -> ApiException.missing("Dispositivo"));
    if (!s.isActive() || !d.isActive())
      throw ApiException.conflict("INACTIVE_RESOURCE", "Tipo ou dispositivo inativo");
    ProductionBatch b = new ProductionBatch();
    b.setOperationId(r.operationId());
    b.setTarget(r.target());
    b.setScrewType(s);
    b.setDevice(d);
    b.setOperator(current.get());
    return Responses.BatchView.of(batches.save(b));
  }

  public Page<Responses.BatchView> list(Pageable p) {
    return batches.findAll(p).map(Responses.BatchView::of);
  }

  public Responses.BatchView get(Long id) {
    return Responses.BatchView.of(find(id));
  }

  private ProductionBatch find(Long id) {
    return batches.findById(id).orElseThrow(() -> ApiException.missing("Lote"));
  }

  public Page<Responses.EventView> events(Long id, Pageable p) {
    find(id);
    return events.findByBatchIdOrderByReceivedAtDesc(id, p).map(Responses.EventView::of);
  }

  public List<Responses.PauseView> pauses(Long id) {
    find(id);
    return pauses.findByBatchIdOrderByPausedAtAsc(id).stream()
        .map(Responses.PauseView::of)
        .toList();
  }

  public Responses.BatchView transition(Long id, String action) {
    String deviceId = batches.deviceIdForBatch(id).orElseThrow(() -> ApiException.missing("Lote"));
    Device d =
        devices.findLockedById(deviceId).orElseThrow(() -> ApiException.missing("Dispositivo"));
    ProductionBatch b = batches.findLockedById(id).orElseThrow(() -> ApiException.missing("Lote"));
    User actor = current.get();
    if (actor.getRole() != Role.ADMIN && !actor.getId().equals(b.getOperator().getId()))
      throw ApiException.forbidden(
          "Somente o operador responsável ou administrador pode alterar o lote");
    Instant now = Instant.now();
    switch (action) {
      case "start" -> {
        require(b, BatchStatus.CONFIGURADO);
        if (!d.isActive()) throw ApiException.conflict("DEVICE_INACTIVE", "Dispositivo inativo");
        if (batches.existsByDeviceIdAndStatusInAndIdNot(
            d.getId(), List.of(BatchStatus.EM_EXECUCAO, BatchStatus.PAUSADO), id))
          throw ApiException.conflict("DEVICE_BUSY", "Dispositivo já possui lote aberto");
        b.setStartedAt(now);
        b.setStatus(BatchStatus.EM_EXECUCAO);
      }
      case "pause" -> {
        require(b, BatchStatus.EM_EXECUCAO);
        ProductionPause p = new ProductionPause();
        p.setBatch(b);
        p.setPausedAt(now);
        p.setPausedBy(actor);
        pauses.save(p);
        b.setStatus(BatchStatus.PAUSADO);
      }
      case "resume" -> {
        require(b, BatchStatus.PAUSADO);
        if (!d.isActive()) throw ApiException.conflict("DEVICE_INACTIVE", "Dispositivo inativo");
        ProductionPause p =
            pauses
                .findByBatchIdAndResumedAtIsNull(id)
                .orElseThrow(
                    () -> ApiException.conflict("PAUSE_NOT_FOUND", "Pausa aberta não encontrada"));
        p.setResumedAt(now);
        p.setResumedBy(actor);
        b.setStatus(BatchStatus.EM_EXECUCAO);
      }
      case "finish" -> {
        if (b.getStatus() != BatchStatus.EM_EXECUCAO && b.getStatus() != BatchStatus.PAUSADO)
          throw ApiException.conflict(
              "INVALID_BATCH_STATE", "O lote deve estar iniciado para finalizar");
        b.setFinishedAt(now);
        b.setStatus(BatchStatus.FINALIZADO);
      }
      default -> throw new IllegalArgumentException("Ação inválida");
    }
    return Responses.BatchView.of(b);
  }

  private void require(ProductionBatch b, BatchStatus expected) {
    if (b.getStatus() != expected)
      throw ApiException.conflict(
          "INVALID_BATCH_STATE", "Status atual: " + b.getStatus() + "; esperado: " + expected);
  }
}
