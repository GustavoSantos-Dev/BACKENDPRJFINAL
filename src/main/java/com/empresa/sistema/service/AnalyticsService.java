package com.empresa.sistema.service;

import com.empresa.sistema.dto.Responses;
import com.empresa.sistema.entity.BatchStatus;
import com.empresa.sistema.repository.ProductionBatchRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
  private final JdbcTemplate jdbc;
  private final ProductionBatchRepository batches;
  private final ZoneId zone;

  public AnalyticsService(
      JdbcTemplate jdbc, ProductionBatchRepository batches, @Value("${app.zone}") String zone) {
    this.jdbc = jdbc;
    this.batches = batches;
    this.zone = ZoneId.of(zone);
  }

  public ZoneId zone() {
    return zone;
  }

  public void validate(LocalDate from, LocalDate to) {
    if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366)
      throw new IllegalArgumentException("Período deve ter entre 1 e 367 dias");
  }

  public Map<String, Object> daily(LocalDate date) {
    return summary(date, date);
  }

  public Map<String, Object> summary(LocalDate from, LocalDate to) {
    validate(from, to);
    Timestamp start = Timestamp.from(from.atStartOfDay(zone).toInstant()),
        end = Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
    Long pieces =
        jdbc.queryForObject(
            "select count(*) from production_events where detected_at >= ? and detected_at < ?",
            Long.class,
            start,
            end);
    Long finished =
        jdbc.queryForObject(
            "select count(*) from production_batches where finished_at >= ? and finished_at < ?",
            Long.class,
            start,
            end);
    return Map.of(
        "from",
        from,
        "to",
        to,
        "zone",
        zone.toString(),
        "totalPieces",
        pieces,
        "finishedBatches",
        finished);
  }

  public List<Map<String, Object>> hourly(LocalDate from, LocalDate to) {
    validate(from, to);
    // Agrupa no banco em UTC; o Java converte para São Paulo sem tabelas de fuso no MySQL.
    var rows =
        jdbc.queryForList(
            "select DATE_FORMAT(detected_at, '%Y-%m-%d %H:00:00') as hour_utc, count(*) as pieces"
                + " from production_events where detected_at >= ? and detected_at < ? group by 1"
                + " order by 1",
            Timestamp.from(from.atStartOfDay(zone).toInstant()),
            Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
    var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    var hours = new TreeMap<LocalDateTime, Long>();
    for (var row : rows) {
      LocalDateTime utc = LocalDateTime.parse(row.get("hour_utc").toString(), formatter);
      LocalDateTime local = utc.toInstant(ZoneOffset.UTC).atZone(zone).toLocalDateTime();
      hours.merge(local, ((Number) row.get("pieces")).longValue(), Long::sum);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    hours.forEach((hour, count) -> result.add(Map.of("hour", hour.toString(), "pieces", count)));
    return result;
  }

  public List<Map<String, Object>> history(LocalDate from, LocalDate to, int page, int size) {
    validate(from, to);
    if (page < 0 || size < 1 || size > 200 || page > 100000)
      throw new IllegalArgumentException("Paginação inválida");
    return jdbc.queryForList(
        "select"
            + " b.id,b.operation_id,b.target,b.current_count,b.status,b.started_at,b.finished_at,s.name"
            + " as screw_type,100.0*b.current_count/b.target as progress from production_batches b"
            + " join screw_types s on s.id=b.screw_type_id where b.created_at < ? and"
            + " (b.finished_at is null or b.finished_at >= ?) order by b.id desc limit ? offset ?",
        Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()),
        Timestamp.from(from.atStartOfDay(zone).toInstant()),
        size,
        page * size);
  }

  public List<Responses.BatchView> current() {
    return batches.findByStatusIn(List.of(BatchStatus.EM_EXECUCAO, BatchStatus.PAUSADO)).stream()
        .map(Responses.BatchView::of)
        .toList();
  }
}
