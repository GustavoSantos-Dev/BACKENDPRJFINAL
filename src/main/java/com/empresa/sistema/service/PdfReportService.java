package com.empresa.sistema.service;

import java.io.*;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PdfReportService {
  private final AnalyticsService analytics;

  public PdfReportService(AnalyticsService analytics) {
    this.analytics = analytics;
  }

  @Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public byte[] generate(LocalDate from, LocalDate to) throws IOException {
    Map<String, Object> summary = analytics.summary(from, to);
    List<String> lines = new ArrayList<>();
    lines.add("RELATORIO DE PRODUCAO");
    lines.add("Periodo: " + from + " a " + to + " | Fuso: " + analytics.zone());
    lines.add("Total de pecas no periodo: " + summary.get("totalPieces"));
    lines.add("Lotes finalizados no periodo: " + summary.get("finishedBatches"));
    lines.add("Gerado em: " + java.time.ZonedDateTime.now(analytics.zone()));
    lines.add("");
    lines.add("LOTES (totais de cada lote; podem ultrapassar o periodo)");
    int page = 0;
    while (true) {
      var rows = analytics.history(from, to, page++, 200);
      if (rows.isEmpty()) break;
      for (var b : rows) {
        lines.add(
            "Operacao: "
                + b.get("operation_id")
                + " | Tipo: "
                + b.get("screw_type")
                + " | Status: "
                + b.get("status"));
        lines.add(
            "Meta: "
                + b.get("target")
                + " | Producao: "
                + b.get("current_count")
                + " | Cumprimento: "
                + b.get("progress")
                + "%");
        lines.add("Inicio: " + b.get("started_at") + " | Termino: " + b.get("finished_at"));
        lines.add("");
      }
      if (rows.size() < 200) break;
    }
    lines.add("PRODUCAO POR HORA (horas sem eventos equivalem a zero)");
    for (var h : analytics.hourly(from, to))
      lines.add(h.get("hour") + " | Pecas: " + h.get("pieces"));
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      List<String> wrapped = new ArrayList<>();
      for (String line : lines) wrap(ascii(line), font, wrapped);
      for (int offset = 0; offset < wrapped.size(); offset += 48) {
        PDPage p = new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
        doc.addPage(p);
        try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
          cs.beginText();
          cs.setFont(font, 10);
          cs.setLeading(15);
          cs.newLineAtOffset(40, 790);
          for (int i = offset; i < Math.min(offset + 48, wrapped.size()); i++) {
            cs.showText(wrapped.get(i));
            cs.newLine();
          }
          cs.endText();
          cs.beginText();
          cs.setFont(font, 9);
          cs.newLineAtOffset(40, 30);
          cs.showText("Pagina " + doc.getNumberOfPages());
          cs.endText();
        }
      }
      doc.save(output);
      return output.toByteArray();
    }
  }

  private static String ascii(String line) {
    return Normalizer.normalize(line, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^\\x20-\\x7E]", "?");
  }

  private static void wrap(String line, PDFont font, List<String> out) throws IOException {
    if (line.isEmpty()) {
      out.add("");
      return;
    }
    String current = "";
    for (String word : line.split(" ")) {
      String candidate = current.isEmpty() ? word : current + " " + word;
      if (font.getStringWidth(candidate) / 1000 * 10 <= 510) {
        current = candidate;
        continue;
      }
      if (!current.isEmpty()) {
        out.add(current);
        current = "";
      }
      while (font.getStringWidth(word) / 1000 * 10 > 510) {
        int end = 1;
        while (end < word.length()
            && font.getStringWidth(word.substring(0, end + 1)) / 1000 * 10 <= 510) end++;
        out.add(word.substring(0, end));
        word = word.substring(end);
      }
      current = word;
    }
    if (!current.isEmpty()) out.add(current);
  }
}
