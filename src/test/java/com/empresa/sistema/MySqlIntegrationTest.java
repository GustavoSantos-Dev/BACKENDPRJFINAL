package com.empresa.sistema;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "MYSQL_TEST_URL",
    matches = ".+")
class MySqlIntegrationTest extends ApiContract {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    String url = System.getenv("MYSQL_TEST_URL");
    if (url == null
        || !url.startsWith("jdbc:mysql://")
        || !url.split("\\?", 2)[0].endsWith("/production_test")) {
      throw new IllegalArgumentException(
          "Use um banco exclusivo de testes chamado production_test");
    }
    String user = System.getenv("MYSQL_TEST_USER");
    String password = System.getenv("MYSQL_TEST_PASSWORD");
    if (user == null || password == null)
      throw new IllegalArgumentException("Configure as credenciais do banco de testes");
    r.add("spring.datasource.url", () -> url);
    r.add("spring.datasource.username", () -> user);
    r.add("spring.datasource.password", () -> password);
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void hourlyAggregationAndPdfWorkOnRealMySql() throws Exception {
    action("start");
    send(event(UUID.randomUUID(), Instant.now()));
    Reply unlocked =
        request(
            "POST",
            "/api/auth/analytics/unlock",
            Map.of("password", "AnalyticsPassword123"),
            token,
            null);
    String elevated = tree(unlocked).get("accessToken").asText();
    LocalDate date = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    String range = "?from=" + date + "&to=" + date;
    Reply hourly = request("GET", "/api/analytics/hourly" + range, null, elevated, null);
    assertThat(hourly.status()).isEqualTo(200);
    assertThat(tree(hourly).get(0).get("pieces").asLong()).isEqualTo(1);
    var req =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(
                    "http://localhost:" + port + "/api/analytics/report/pdf" + range))
            .header("Authorization", "Bearer " + elevated)
            .GET()
            .build();
    var pdf = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
    assertThat(pdf.statusCode()).isEqualTo(200);
    try (var doc = Loader.loadPDF(pdf.body())) {
      assertThat(new PDFTextStripper().getText(doc))
          .contains("RELATORIO DE PRODUCAO", "Total de pecas no periodo: 1", operation);
    }
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/qa"));
    java.nio.file.Files.write(java.nio.file.Path.of("target/qa/report.pdf"), pdf.body());
  }
}
