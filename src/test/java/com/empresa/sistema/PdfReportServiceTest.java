package com.empresa.sistema;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.empresa.sistema.service.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfReportServiceTest {
  @Test
  void paginatesAndIncludesAllBatchesAndTotals() throws Exception {
    AnalyticsService analytics = mock(AnalyticsService.class);
    LocalDate from = LocalDate.of(2026, 10, 7);
    when(analytics.zone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
    when(analytics.summary(from, from))
        .thenReturn(Map.of("totalPieces", 250, "finishedBatches", 45));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 0; i < 45; i++)
      rows.add(
          Map.of(
              "operation_id",
              "OP-" + i,
              "screw_type",
              "Parafuso aço " + "Descrição longa ".repeat(12),
              "status",
              "FINALIZADO",
              "target",
              100,
              "current_count",
              90,
              "progress",
              90,
              "started_at",
              "2026-10-07T12:00:00Z",
              "finished_at",
              "2026-10-07T14:00:00Z"));
    when(analytics.history(from, from, 0, 200)).thenReturn(rows);
    when(analytics.hourly(from, from))
        .thenReturn(List.of(Map.of("hour", "2026-10-07T09:00", "pieces", 250)));
    byte[] pdf = new PdfReportService(analytics).generate(from, from);
    try (var doc = Loader.loadPDF(pdf)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(1);
      String text = new PDFTextStripper().getText(doc);
      assertThat(text)
          .contains(
              "Total de pecas no periodo: 250",
              "OP-44",
              "Parafuso aco",
              "Descricao longa",
              "PRODUCAO POR HORA");
    }
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/qa"));
    java.nio.file.Files.write(java.nio.file.Path.of("target/qa/report-layout.pdf"), pdf);
  }
}
