package pe.com.scotiabank.tapp.auditquery;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MongoApiIntegrationTest.FrozenTime.class)
@EnabledIfEnvironmentVariable(named = "MONGODB_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MongoApiIntegrationTest {
  private static final String DATABASE = "audit_query_test_" + UUID.randomUUID().toString().replace("-", "");
  private static final String COLLECTION = "tapp_audit_events";
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  @Autowired MongoTemplate mongo;
  @Autowired Environment environment;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.mongodb.uri", () -> System.getenv("MONGODB_TEST_URI"));
    registry.add("spring.mongodb.database", () -> DATABASE);
    registry.add("app.mongodb.collection", () -> COLLECTION);
  }

  @BeforeEach
  void seedIsolatedDatabase() {
    assertEquals(DATABASE, mongo.getDb().getName());
    mongo.getCollection(COLLECTION).deleteMany(new Document());
    mongo.getCollection(COLLECTION).insertMany(List.of(
        event(1, "TAPP", "HIGH", "demo.usuario", "2026-11-13T16:00:00Z"),
        event(2, "B3QX", "LOW", "demo.literal[0]", "2026-11-12T16:00:00Z"),
        event(3, "TAPP", "NORMAL", "demo.operador", "2026-11-13T16:00:00Z"),
        event(4, "B3QX", "HIGH", "demo.usuario", "2026-11-13T00:30:00Z")));
  }

  @AfterAll
  void cleanIsolatedDatabase() {
    if (mongo.getDb().getName().equals(DATABASE)) mongo.getDb().drop();
  }

  @Test
  void listsAndSortsWithStablePagination() throws Exception {
    Document first = json("/events?size=1");
    Document second = json("/events?size=1&page=1");
    assertAll(() -> assertEquals(4, ((Number) first.get("total")).intValue()),
        () -> assertEquals("uid-3", first.getList("items", Document.class).getFirst().getString("uid")),
        () -> assertEquals("uid-1", second.getList("items", Document.class).getFirst().getString("uid")));
  }

  @Test
  void combinesApplicationAndPriorityFilters() throws Exception {
    Document result = json("/events?application=TAPP&priority=HIGH");
    assertEquals(1, ((Number) result.get("total")).intValue());
    assertEquals("uid-1", result.getList("items", Document.class).getFirst().getString("uid"));
  }

  @Test
  void treatsSearchTextAsLiteralInsteadOfRegex() throws Exception {
    assertEquals(0, ((Number) json("/events?q=" + encode(".*")).get("total")).intValue());
    assertEquals(1, ((Number) json("/events?q=" + encode("LITERAL[0]")).get("total")).intValue());
  }

  @Test
  void searchesOperationDocumentAndSession() throws Exception {
    assertEquals(1, ((Number) json("/events?q=OP-001").get("total")).intValue());
    assertEquals(1, ((Number) json("/events?q=DOC-002").get("total")).intValue());
    assertEquals(1, ((Number) json("/events?q=session-003").get("total")).intValue());
  }

  @Test
  void supportsHalfOpenDateRangesWithOffsets() throws Exception {
    Document result = json("/events?from=" + encode("2026-11-13T00:00:00-05:00")
        + "&to=" + encode("2026-11-14T00:00:00-05:00"));
    assertEquals(2, ((Number) result.get("total")).intValue());
  }

  @Test
  void summarizesEntireFilteredSetAndUsesLimaDay() throws Exception {
    Document result = json("/summary");
    assertAll(() -> assertEquals(4, result.getInteger("total")),
        () -> assertEquals(2, result.getInteger("applications")),
        () -> assertEquals(2, result.getInteger("highPriority")),
        () -> assertEquals(2, result.getInteger("today")));
    Document filtered = json("/summary?application=B3QX");
    assertAll(() -> assertEquals(2, filtered.getInteger("total")),
        () -> assertEquals(1, filtered.getInteger("highPriority")),
        () -> assertEquals(0, filtered.getInteger("today")));
  }

  @Test
  void preservesDynamicPayloadAndNormalizesBsonForDetail() throws Exception {
    Document result = json("/events/uid-1");
    assertAll(() -> assertEquals("000000000000000000000001", result.getString("_id")),
        () -> assertEquals("2026-11-13T16:00:00Z", result.getString("event_timestamp")));
    Document header = result.get("event_message", Document.class).get("channel_payload_header", Document.class);
    assertTrue(header.containsKey("nullable"));
    assertNull(header.get("nullable"));
    assertEquals("kept", header.getList("dynamic", Document.class).getFirst().getString("value"));
  }

  @Test
  void returnsNotFoundForUnknownUid() throws Exception {
    HttpResponse<String> response = get("/events/not-found");
    assertEquals(404, response.statusCode());
    assertEquals("EVENT_NOT_FOUND", Document.parse(response.body()).getString("code"));
  }

  @Test
  void validatesParametersAndDoesNotExposeExceptions() throws Exception {
    for (String path : List.of("/events?size=101", "/events?page=-1", "/events?from=invalid",
        "/events?sort=" + encode("$where"), "/events?direction=drop", "/events?page=abc",
        "/events?q=" + "a".repeat(121), "/events?from=2026-11-14T00:00:00Z&to=2026-11-13T00:00:00Z")) {
      HttpResponse<String> response = get(path);
      assertEquals(400, response.statusCode(), path);
      assertFalse(response.body().contains("stackTrace"));
      assertFalse(response.body().contains("mongodb://"));
    }
  }

  @Test
  void returnsCatalogsFromStoredValuesAndChecksConnection() throws Exception {
    Document catalogs = json("/catalogs");
    assertEquals(List.of("B3QX", "TAPP"), catalogs.getList("applications", String.class));
    assertEquals(List.of("HIGH", "LOW", "NORMAL"), catalogs.getList("priorities", String.class));
    assertEquals("UP", json("/health").getString("status"));
  }

  @Test
  void returnsEmptyResultsAndSummaryWithoutInventingData() throws Exception {
    assertEquals(List.of(), json("/events?application=UNKNOWN").getList("items", Document.class));
    Document summary = json("/summary?application=UNKNOWN");
    summary.values().forEach(value -> assertEquals(0, ((Number) value).intValue()));
  }

  @Test
  void servesAngularFromTheSameApplication() throws Exception {
    HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base() + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<app-root>"));
    assertTrue(response.body().contains("main-"));
  }

  @Test
  void computesAnalyticsAcrossAllEventsAndGroupsDatesInLima() throws Exception {
    assertEquals(1, json("/events?size=1").getList("items", Document.class).size());
    Document analytics = json("/analytics");
    assertEquals(4, analytics.get("summary", Document.class).getInteger("total"));
    List<Document> daily = analytics.getList("daily", Document.class);
    assertEquals(List.of("2026-11-12", "2026-11-13"), daily.stream().map(row -> row.getString("key")).toList());
    assertEquals(List.of(2, 2), daily.stream().map(row -> row.getInteger("count")).toList());
    List<Document> hours = analytics.getList("hours", Document.class);
    assertEquals(List.of("11", "19"), hours.stream().map(row -> row.getString("key")).toList());
    assertEquals(List.of(3, 1), hours.stream().map(row -> row.getInteger("count")).toList());
    assertEquals("America/Lima", analytics.getString("timezone"));
    assertEquals("2026-11-12", analytics.getString("rangeStart"));
    assertEquals("2026-11-13", analytics.getString("rangeEnd"));
    for (String dimension : List.of("daily", "hours", "applications", "priorities", "actions")) {
      assertEquals(4, analytics.getList(dimension, Document.class).stream().mapToInt(row -> row.getInteger("count")).sum());
    }
  }

  @Test
  void analyticsUsesTheSameCombinedFiltersAsTheEventList() throws Exception {
    Document analytics = json("/analytics?application=TAPP&priority=HIGH&q=OP-001&from=2026-11-13T05:00:00Z&to=2026-11-14T05:00:00Z");
    assertEquals(1, analytics.get("summary", Document.class).getInteger("total"));
    assertEquals(List.of(new Document("key", "TAPP").append("count", 1)), analytics.getList("applications", Document.class));
    assertEquals(List.of(new Document("key", "HIGH").append("count", 1)), analytics.getList("priorities", Document.class));
    assertEquals("2026-11-13", analytics.getString("rangeStart"));
    assertEquals("2026-11-13", analytics.getString("rangeEnd"));
  }

  @Test
  void analyticsKeepsUnknownCategoriesAndCountsEventsWithoutBsonDates() throws Exception {
    mongo.getCollection(COLLECTION).insertMany(List.of(new Document("uid", "undated"),
        new Document("uid", "legacy-date").append("event_timestamp", "2026-11-13T16:00:00Z")));
    Document analytics = json("/analytics");
    assertEquals(6, analytics.get("summary", Document.class).getInteger("total"));
    assertEquals(2, analytics.getInteger("undated"));
    assertEquals(4, analytics.getList("daily", Document.class).stream().mapToInt(row -> row.getInteger("count")).sum());
    for (String dimension : List.of("applications", "priorities", "actions")) {
      List<Document> groups = analytics.getList(dimension, Document.class);
      assertEquals(6, groups.stream().mapToInt(row -> row.getInteger("count")).sum());
      assertEquals(2, groups.stream().filter(row -> "Sin dato".equals(row.getString("key"))).findFirst().orElseThrow().getInteger("count"));
    }
  }

  @Test
  void analyticsHandlesNoResultsAndInvalidDateFilters() throws Exception {
    Document analytics = json("/analytics?application=UNKNOWN");
    assertEquals(0, analytics.get("summary", Document.class).getInteger("total"));
    for (String dimension : List.of("daily", "hours", "applications", "priorities", "actions")) {
      assertEquals(List.of(), analytics.getList(dimension, Document.class));
    }
    assertEquals(400, get("/analytics?from=not-a-date").statusCode());
    assertEquals(400, get("/analytics?from=2026-11-14T00:00:00Z&to=2026-11-13T00:00:00Z").statusCode());
  }

  @Test
  void exposesNoWriteOperation() throws Exception {
    HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/audit/events"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(405, response.statusCode());
    assertEquals(4, mongo.getCollection(COLLECTION).countDocuments());
  }

  private Document json(String path) throws Exception {
    HttpResponse<String> response = get(path);
    assertEquals(200, response.statusCode(), response.body());
    return Document.parse(response.body());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/audit" + path))
        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private String base() { return "http://127.0.0.1:" + environment.getProperty("local.server.port"); }
  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

  private static Document event(int id, String app, String priority, String user, String timestamp) {
    Document header = new Document("nullable", null).append("dynamic", List.of(new Document("value", "kept")))
        .append("channel_payload_detail", List.of(new Document("key", "amount").append("value", "150.00")));
    Document message = new Document("app_code", app).append("app_operation_description", "Operación de prueba")
        .append("operation_number", "OP-%03d".formatted(id)).append("number_id", "DOC-%03d".formatted(id))
        .append("channel_payload_header", header);
    return new Document("_id", new ObjectId("%024x".formatted(id))).append("uid", "uid-" + id)
        .append("account_name", user).append("event_message", message).append("event_priority", priority)
        .append("audit_action", "EXECUTE").append("event_category", "TERMINATED")
        .append("event_type", "INFORMATION").append("event_log_classification", "AUDIT_LOG")
        .append("session_identifier", "session-%03d".formatted(id))
        .append("event_timestamp", Date.from(Instant.parse(timestamp))).append("log_timestamp", Date.from(Instant.parse(timestamp)));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FrozenTime {
    @Bean @Primary
    Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-13T20:00:00Z"), ZoneId.of("America/Lima")); }
  }
}
