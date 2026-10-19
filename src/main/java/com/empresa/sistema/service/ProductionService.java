package com.empresa.sistema.service;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.entity.*;
import com.empresa.sistema.exception.ApiException;
import com.empresa.sistema.repository.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductionService {
  private final DeviceService deviceAuth;
  private final DeviceRepository devices;
  private final ProductionBatchRepository batches;
  private final ProductionEventRepository events;
  private final ProductionPauseRepository pauses;

  public ProductionService(
      DeviceService deviceAuth,
      DeviceRepository devices,
      ProductionBatchRepository batches,
      ProductionEventRepository events,
      ProductionPauseRepository pauses) {
    this.deviceAuth = deviceAuth;
    this.devices = devices;
    this.batches = batches;
    this.events = events;
    this.pauses = pauses;
  }

  public Responses.EventAccepted accept(Requests.Event r, String key) {
    devices
        .findLockedById(r.deviceId())
        .orElseThrow(
            () ->
                new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "DEVICE_UNAUTHORIZED",
                    "Dispositivo não autorizado"));
    Device d = deviceAuth.authenticate(r.deviceId(), key);
    ProductionBatch b =
        batches
            .findLockedByOperationId(r.operationId())
            .orElseThrow(() -> ApiException.missing("Lote"));
    if (!b.getDevice().getId().equals(d.getId()))
      throw ApiException.forbidden("Dispositivo não associado ao lote");
    Instant detected = r.timestamp().truncatedTo(ChronoUnit.MICROS);
    var previous = events.findById(r.eventId());
    if (previous.isPresent()) {
      ProductionEvent p = previous.get();
      if (!p.getBatch().getId().equals(b.getId())
          || !p.getDevice().getId().equals(r.deviceId())
          || !p.getDetectedAt().equals(detected))
        throw ApiException.conflict("EVENT_ID_REUSED", "eventId já utilizado com outro conteúdo");
      return new Responses.EventAccepted(true, true, b.getCurrentCount(), r.eventId());
    }
    if (b.getStatus() != BatchStatus.EM_EXECUCAO)
      throw ApiException.conflict("BATCH_" + b.getStatus(), "Lote não está em execução");
    Instant now = Instant.now();
    if (detected.isBefore(b.getStartedAt()) || detected.isAfter(now.plusSeconds(30)))
      throw ApiException.conflict("INVALID_EVENT_TIME", "Evento anterior ao início ou no futuro");
    for (ProductionPause p : pauses.findByBatchIdOrderByPausedAtAsc(b.getId())) {
      if (!detected.isBefore(p.getPausedAt())
          && (p.getResumedAt() == null || detected.isBefore(p.getResumedAt())))
        throw ApiException.conflict("EVENT_DURING_PAUSE", "Evento detectado durante uma pausa");
    }
    if (b.getCurrentCount() == Long.MAX_VALUE)
      throw ApiException.conflict("COUNT_LIMIT", "Limite de contagem atingido");
    long next = b.getCurrentCount() + 1;
    ProductionEvent e = new ProductionEvent();
    e.setEventId(r.eventId());
    e.setDevice(d);
    e.setBatch(b);
    e.setDetectedAt(detected);
    e.setReceivedAt(now);
    e.setCountAfter(next);
    events.saveAndFlush(e);
    b.setCurrentCount(next);
    return new Responses.EventAccepted(true, false, next, r.eventId());
  }

  public List<Responses.BatchView> state(String id, String key) {
    deviceAuth.authenticate(id, key);
    return batches.findByStatusIn(List.of(BatchStatus.EM_EXECUCAO, BatchStatus.PAUSADO)).stream()
        .filter(b -> b.getDevice().getId().equals(id))
        .map(Responses.BatchView::of)
        .toList();
  }
}
