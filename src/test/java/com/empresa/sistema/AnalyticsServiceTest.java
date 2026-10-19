package com.empresa.sistema;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.empresa.sistema.repository.ProductionBatchRepository;
import com.empresa.sistema.service.AnalyticsService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsServiceTest {
  @Test
  void convertsUtcBucketsToSaoPauloAtDayBoundary() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), any(Timestamp.class), any(Timestamp.class)))
        .thenReturn(List.of(Map.of("hour_utc", "2026-10-07 03:00:00", "pieces", 7L)));
    var analytics =
        new AnalyticsService(jdbc, mock(ProductionBatchRepository.class), "America/Sao_Paulo");
    assertThat(analytics.hourly(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7)))
        .containsExactly(Map.of("hour", "2026-10-07T00:00", "pieces", 7L));
  }

  @Test
  void mergesRepeatedLocalHourWhenHistoricalDstEnds() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), any(Timestamp.class), any(Timestamp.class)))
        .thenReturn(
            List.of(
                Map.of("hour_utc", "2018-02-18 01:00:00", "pieces", 2L),
                Map.of("hour_utc", "2018-02-18 02:00:00", "pieces", 3L)));
    var analytics =
        new AnalyticsService(jdbc, mock(ProductionBatchRepository.class), "America/Sao_Paulo");
    assertThat(analytics.hourly(LocalDate.of(2018, 2, 17), LocalDate.of(2018, 2, 17)))
        .containsExactly(Map.of("hour", "2018-02-17T23:00", "pieces", 5L));
  }
}
