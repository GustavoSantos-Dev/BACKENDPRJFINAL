package com.empresa.sistema;

import static org.assertj.core.api.Assertions.*;

import com.empresa.sistema.entity.*;
import com.empresa.sistema.repository.*;
import com.empresa.sistema.service.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

abstract class ApiContract {
  @Test
  void finishRacingWithEventsPreservesAcceptedCount() throws Exception {
    action("start");
    send(event(UUID.randomUUID(), Instant.now()));
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Reply>> futures = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        var e = event(UUID.randomUUID(), Instant.now());
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return send(e);
                }));
      }
      Future<Reply> finish =
          pool.submit(
              () -> {
                start.await();
                return action("finish");
              });
      start.countDown();
      long accepted = 1;
      for (var f : futures) {
        Reply r = f.get(35, TimeUnit.SECONDS);
        assertThat(r.status()).isIn(200, 409);
        if (r.status() == 200) accepted++;
      }
      assertThat(finish.get(35, TimeUnit.SECONDS).status()).isEqualTo(200);
      Reply state = request("GET", "/api/batches/" + batchId, null, token, null);
      assertThat(tree(state).get("currentCount").asLong()).isEqualTo(accepted);
      assertThat(events.count()).isEqualTo(accepted);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void machineCannotRunTwoBatches() throws Exception {
    action("start");
    var state = tree(request("GET", "/api/batches/" + batchId, null, token, null));
    Reply second =
        request(
            "POST",
            "/api/batches",
            Map.of(
                "operationId",
                "SECOND-" + UUID.randomUUID(),
                "target",
                100,
                "screwTypeId",
                state.get("screwTypeId").asLong(),
                "deviceId",
                deviceId),
            token,
            null);
    assertThat(second.status()).isEqualTo(201);
    long secondId = tree(second).get("id").asLong();
    assertThat(request("POST", "/api/batches/" + secondId + "/start", null, token, null).status())
        .isEqualTo(409);
  }

  @Test
  void finishPausedBatchDoesNotInventResume() throws Exception {
    action("start");
    action("pause");
    assertThat(action("finish").status()).isEqualTo(200);
    Reply pauses = request("GET", "/api/batches/" + batchId + "/pauses", null, token, null);
    assertThat(tree(pauses).get(0).get("resumedAt").isNull()).isTrue();
  }

  @Test
  void rotatingDeviceKeyRevokesOldKey() throws Exception {
    action("start");
    Reply rotated = request("POST", "/api/devices/" + deviceId + "/rotate-key", null, token, null);
    assertThat(rotated.status()).isEqualTo(200);
    assertThat(send(event(UUID.randomUUID(), Instant.now())).status()).isEqualTo(401);
    key = tree(rotated).get("apiKey").asText();
    assertThat(send(event(UUID.randomUUID(), Instant.now())).status()).isEqualTo(200);
  }

  @Test
  void securityErrorsAreValidJson() throws Exception {
    Reply r = request("GET", "/api/batches", null, null, null);
    assertThat(r.status()).isEqualTo(401);
    assertThat(tree(r).get("code").asText()).isEqualTo("UNAUTHORIZED");
    assertThat(tree(r).get("path").asText()).isEqualTo("/api/batches");
  }

  @Test
  void validatesPhotoUrls() throws Exception {
    assertThat(
            request(
                    "POST",
                    "/api/screws",
                    Map.of(
                        "name",
                        "M8",
                        "description",
                        "Foto",
                        "photoUrl",
                        "https://example.com/screw.jpg"),
                    token,
                    null)
                .status())
        .isEqualTo(201);
    assertThat(
            request(
                    "POST",
                    "/api/screws",
                    Map.of(
                        "name", "M8", "description", "Foto", "photoUrl", "https://example.com/a b"),
                    token,
                    null)
                .status())
        .isEqualTo(400);
  }

  @Test
  void exposesOpenApiContract() throws Exception {
    Reply r = request("GET", "/v3/api-docs", null, null, null);
    assertThat(r.status()).isEqualTo(200);
    assertThat(tree(r).get("paths").has("/api/production/events")).isTrue();
  }

  @Value("${local.server.port}")
  int port;

  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProductionEventRepository events;
  final JsonMapper json = JsonMapper.builder().build();
  final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  String token, key, operation, deviceId;
  long batchId;

  record Reply(int status, String body) {}

  Reply request(String method, String path, Object body, String bearer, String deviceKey)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json");
    if (bearer != null) b.header("Authorization", "Bearer " + bearer);
    if (deviceKey != null) b.header("X-Device-Key", deviceKey);
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    return new Reply(r.statusCode(), r.body());
  }

  JsonNode tree(Reply r) {
    return json.readTree(r.body());
  }

  Reply login(String email, String password) throws Exception {
    return request(
        "POST", "/api/auth/login", Map.of("email", email, "password", password), null, null);
  }

  Reply action(String action) throws Exception {
    return request("POST", "/api/batches/" + batchId + "/" + action, null, token, null);
  }

  Map<String, Object> event(UUID id, Instant at) {
    return Map.of(
        "deviceId",
        deviceId,
        "operationId",
        operation,
        "eventId",
        id.toString(),
        "timestamp",
        at.toString());
  }

  Reply send(Map<String, Object> e) throws Exception {
    return request("POST", "/api/production/events", e, null, key);
  }

  @BeforeEach
  void setup() throws Exception {
    jdbc.update("delete from production_events");
    jdbc.update("delete from production_pauses");
    jdbc.update("delete from production_batches");
    jdbc.update("delete from devices");
    jdbc.update("delete from screw_types");
    jdbc.update("delete from app_users");
    User admin = new User();
    admin.setName("Admin");
    admin.setEmail("admin@test.com");
    admin.setPasswordHash(encoder.encode("TestPassword123"));
    admin.setAnalyticsPasswordHash(encoder.encode("AnalyticsPassword123"));
    admin.setRole(Role.ADMIN);
    users.save(admin);
    Reply auth = login("admin@test.com", "TestPassword123");
    assertThat(auth.status()).isEqualTo(200);
    token = tree(auth).get("accessToken").asText();
    Reply screw =
        request(
            "POST",
            "/api/screws",
            Map.of("name", "M6", "description", "Parafuso de teste"),
            token,
            null);
    assertThat(screw.status()).isEqualTo(201);
    deviceId = "ESP-" + UUID.randomUUID().toString().substring(0, 8);
    operation = "OP-" + UUID.randomUUID();
    Reply device =
        request(
            "POST", "/api/devices", Map.of("deviceId", deviceId, "name", "Máquina 1"), token, null);
    assertThat(device.status()).isEqualTo(201);
    key = tree(device).get("apiKey").asText();
    Reply batch =
        request(
            "POST",
            "/api/batches",
            Map.of(
                "operationId",
                operation,
                "target",
                100,
                "screwTypeId",
                tree(screw).get("id").asLong(),
                "deviceId",
                deviceId),
            token,
            null);
    assertThat(batch.status()).isEqualTo(201);
    batchId = tree(batch).get("id").asLong();
  }

  @Test
  void loginAndInvalidCredentials() throws Exception {
    assertThat(login("admin@test.com", "TestPassword123").status()).isEqualTo(200);
    assertThat(login("admin@test.com", "wrong").status()).isEqualTo(401);
    assertThat(login("missing@test.com", "wrong").status()).isEqualTo(401);
  }

  @Test
  void batchStartsAtZeroAndRejectsRepeatedStart() throws Exception {
    Reply before = request("GET", "/api/batches/" + batchId, null, token, null);
    assertThat(tree(before).get("currentCount").asLong()).isZero();
    assertThat(tree(action("start")).get("status").asText()).isEqualTo("EM_EXECUCAO");
    assertThat(action("start").status()).isEqualTo(409);
  }

  @Test
  void acceptsAndDeduplicatesEventEvenAfterFinish() throws Exception {
    action("start");
    var e = event(UUID.randomUUID(), Instant.now());
    Reply first = send(e);
    assertThat(first.status()).isEqualTo(200);
    assertThat(tree(first).get("currentCount").asLong()).isEqualTo(1);
    Reply duplicate = send(e);
    assertThat(tree(duplicate).get("duplicate").asBoolean()).isTrue();
    action("finish");
    assertThat(send(e).status()).isEqualTo(200);
    assertThat(events.count()).isEqualTo(1);
  }

  @Test
  void rejectsReusedIdWithDifferentPayload() throws Exception {
    action("start");
    UUID id = UUID.randomUUID();
    send(event(id, Instant.now()));
    Reply r = send(event(id, Instant.now().plusSeconds(1)));
    assertThat(r.status()).isEqualTo(409);
    assertThat(r.body()).contains("EVENT_ID_REUSED");
    assertThat(events.count()).isEqualTo(1);
  }

  @Test
  void rejectsPausedAndFinishedBatches() throws Exception {
    action("start");
    action("pause");
    assertThat(send(event(UUID.randomUUID(), Instant.now())).status()).isEqualTo(409);
    action("resume");
    action("finish");
    assertThat(send(event(UUID.randomUUID(), Instant.now())).status()).isEqualTo(409);
    assertThat(events.count()).isZero();
  }

  @Test
  void rejectsEventsDetectedDuringPreviousPause() throws Exception {
    action("start");
    action("pause");
    Instant during = Instant.now();
    Thread.sleep(10);
    action("resume");
    Reply r = send(event(UUID.randomUUID(), during));
    assertThat(r.status()).isEqualTo(409);
    assertThat(r.body()).contains("EVENT_DURING_PAUSE");
  }

  @Test
  void acceptsOfflineBacklogAfterResume() throws Exception {
    action("start");
    var pending = event(UUID.randomUUID(), Instant.now());
    action("pause");
    action("resume");
    assertThat(send(pending).status()).isEqualTo(200);
  }

  @Test
  void rejectsWrongDeviceKeyAndInvalidTimestamp() throws Exception {
    action("start");
    assertThat(
            request(
                    "POST",
                    "/api/production/events",
                    event(UUID.randomUUID(), Instant.now()),
                    null,
                    "invalid")
                .status())
        .isEqualTo(401);
    assertThat(send(event(UUID.randomUUID(), Instant.now().plusSeconds(120))).status())
        .isEqualTo(409);
    assertThat(send(event(UUID.randomUUID(), Instant.EPOCH)).status()).isEqualTo(409);
    assertThat(events.count()).isZero();
  }

  @Test
  void validatesInputsAndRequiresAuthentication() throws Exception {
    assertThat(request("GET", "/api/batches", null, null, null).status()).isEqualTo(401);
    assertThat(request("POST", "/api/batches", Map.of("target", 0), token, null).status())
        .isEqualTo(400);
    assertThat(
            request("POST", "/api/production/events", Map.of("eventId", "not-uuid"), null, key)
                .status())
        .isEqualTo(400);
  }

  @Test
  void analyticsRequiresSecondPassword() throws Exception {
    String path = "/api/analytics/daily?date=" + LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    assertThat(request("GET", path, null, token, null).status()).isEqualTo(403);
    assertThat(
            request("POST", "/api/auth/analytics/unlock", Map.of("password", "wrong"), token, null)
                .status())
        .isEqualTo(401);
    Reply unlocked =
        request(
            "POST",
            "/api/auth/analytics/unlock",
            Map.of("password", "AnalyticsPassword123"),
            token,
            null);
    assertThat(unlocked.status()).isEqualTo(200);
    String elevated = tree(unlocked).get("accessToken").asText();
    assertThat(request("GET", path, null, elevated, null).status()).isEqualTo(200);
  }

  @Test
  void dailyAnalyticsCountsDetectedEvents() throws Exception {
    action("start");
    send(event(UUID.randomUUID(), Instant.now()));
    send(event(UUID.randomUUID(), Instant.now()));
    action("finish");
    Reply unlocked =
        request(
            "POST",
            "/api/auth/analytics/unlock",
            Map.of("password", "AnalyticsPassword123"),
            token,
            null);
    String elevated = tree(unlocked).get("accessToken").asText();
    Reply daily =
        request(
            "GET",
            "/api/analytics/daily?date=" + LocalDate.now(ZoneId.of("America/Sao_Paulo")),
            null,
            elevated,
            null);
    assertThat(tree(daily).get("totalPieces").asLong()).isEqualTo(2);
    assertThat(tree(daily).get("finishedBatches").asLong()).isEqualTo(1);
  }

  @Test
  void concurrentUniqueEventsDoNotLoseCounts() throws Exception {
    action("start");
    runConcurrent(false);
    Reply state = request("GET", "/api/batches/" + batchId, null, token, null);
    assertThat(tree(state).get("currentCount").asLong()).isEqualTo(24);
    assertThat(events.count()).isEqualTo(24);
  }

  @Test
  void concurrentDuplicateEventsCountOnce() throws Exception {
    action("start");
    runConcurrent(true);
    Reply state = request("GET", "/api/batches/" + batchId, null, token, null);
    assertThat(tree(state).get("currentCount").asLong()).isEqualTo(1);
    assertThat(events.count()).isEqualTo(1);
  }

  private void runConcurrent(boolean duplicate) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    UUID id = UUID.randomUUID();
    Instant at = Instant.now();
    try {
      List<Future<Reply>> futures = new ArrayList<>();
      for (int i = 0; i < 24; i++) {
        var e = event(duplicate ? id : UUID.randomUUID(), at);
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return send(e);
                }));
      }
      start.countDown();
      for (var f : futures) assertThat(f.get(35, TimeUnit.SECONDS).status()).isEqualTo(200);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void inactiveUserCannotReuseToken() throws Exception {
    User u = users.findByEmail("admin@test.com").orElseThrow();
    u.setActive(false);
    users.save(u);
    assertThat(request("GET", "/api/auth/me", null, token, null).status()).isEqualTo(401);
  }

  @Test
  void operatorCannotManageUsersOrUnlockAnalytics() throws Exception {
    User u = new User();
    u.setName("Operador");
    u.setEmail("operator@test.com");
    u.setPasswordHash(encoder.encode("TestPassword123"));
    u.setRole(Role.OPERADOR);
    users.save(u);
    String op = tree(login(u.getEmail(), "TestPassword123")).get("accessToken").asText();
    assertThat(request("GET", "/api/users", null, op, null).status()).isEqualTo(403);
    assertThat(
            request(
                    "POST",
                    "/api/auth/analytics/unlock",
                    Map.of("password", "TestPassword123"),
                    op,
                    null)
                .status())
        .isEqualTo(403);
    assertThat(request("POST", "/api/batches/" + batchId + "/start", null, op, null).status())
        .isEqualTo(403);
  }

  @Test
  void pauseHistoryAndFinalProgressAreRecorded() throws Exception {
    action("start");
    send(event(UUID.randomUUID(), Instant.now()));
    action("pause");
    action("resume");
    Reply r = action("finish");
    assertThat(tree(r).get("progress").asDouble()).isEqualTo(1.0);
    assertThat(tree(r).get("finishedAt").isNull()).isFalse();
    Reply history = request("GET", "/api/batches/" + batchId + "/pauses", null, token, null);
    assertThat(tree(history).size()).isEqualTo(1);
    assertThat(tree(history).get(0).get("resumedAt").isNull()).isFalse();
  }
}
