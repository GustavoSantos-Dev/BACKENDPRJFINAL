package com.empresa.sistema.controller;

import com.empresa.sistema.dto.Responses;
import com.empresa.sistema.service.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
  private final AnalyticsService analytics;
  private final PdfReportService reports;

  public AnalyticsController(AnalyticsService analytics, PdfReportService reports) {
    this.analytics = analytics;
    this.reports = reports;
  }

  @GetMapping("/daily")
  public Map<String, Object> daily(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return analytics.daily(date);
  }

  @GetMapping("/hourly")
  public List<Map<String, Object>> hourly(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return analytics.hourly(from, to);
  }

  @GetMapping("/batches")
  public List<Map<String, Object>> batches(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return analytics.history(from, to, page, size);
  }

  @GetMapping("/current-batch")
  public List<Responses.BatchView> current() {
    return analytics.current();
  }

  @GetMapping("/summary")
  public Map<String, Object> summary(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return analytics.summary(from, to);
  }

  @GetMapping(value = "/report/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
      throws java.io.IOException {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=production-" + from + "-" + to + ".pdf")
        .body(reports.generate(from, to));
  }
}
