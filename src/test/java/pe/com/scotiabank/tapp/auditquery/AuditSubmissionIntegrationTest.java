package pe.com.scotiabank.tapp.auditquery;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = AuditQueryApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=100&connectTimeoutMS=100")
class AuditSubmissionIntegrationTest {
  private static final String PATH = "/api/v1/audit/security/log";
  private static final String SUCCESS = "{\"data\":[{\"status\":\"OK\"}]}";
  private static final Map<String, String> HEADERS = Map.of("x-api-version", "v1", "x-channel-id", "WEB",
      "x-originating-appl-code", "TAPP", "x-country-code", "PE");
  private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
  private static final AtomicReference<Recorded> RECORDED = new AtomicReference<>();
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final HttpServer UPSTREAM = upstream();
  private static final String FIXTURE = fixture();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  @Autowired Environment environment;
  @Autowired JsonMapper mapper;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("audit.api.url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + PATH);
    registry.add("audit.api.connect-timeout", () -> "500ms");
    registry.add("audit.api.read-timeout", () -> "700ms");
  }

  @BeforeEach
  void reset() {
    REPLY.set(new Reply(200, SUCCESS, 0));
    RECORDED.set(null);
    CALLS.set(0);
  }

  @AfterAll
  static void stop() { UPSTREAM.stop(0); }

  @Test
  void sendsTheCompleteOriginalPayloadHeadersAndTwentyDetailsFromTheMonolith() throws Exception {
    HttpResponse<String> response = post(FIXTURE, HEADERS);
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(mapper.readTree(SUCCESS), mapper.readTree(response.body()));
    Recorded request = RECORDED.get();
    assertNotNull(request);
    assertEquals("POST", request.method());
    assertEquals(PATH, request.path());
    HEADERS.forEach((name, value) -> assertEquals(value, request.headers().get(name)));
    assertEquals(mapper.readTree(FIXTURE), mapper.readTree(request.body()));
    assertEquals(20, mapper.readTree(request.body()).path("event_message").path("channel_payload_detail").size());
    assertEquals(1, CALLS.get());
  }

  @Test
  void preservesNestedDynamicHeaderListsNullsAndOffsets() throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(FIXTURE);
    ObjectNode header = (ObjectNode) body.path("event_message").path("channel_payload_header");
    header.putNull("nullable");
    header.set("nested", mapper.readTree("{\"items\":[{\"amount\":0,\"enabled\":false},null,\"\"]}"));
    body.put("event_timestamp", "2026-11-13T11:00:00.123-05:00");
    assertEquals(200, post(mapper.writeValueAsString(body), HEADERS).statusCode());
    assertEquals(body, mapper.readTree(RECORDED.get().body()));
  }

  @ParameterizedTest
  @ValueSource(ints = {201, 202})
  void preservesSuccessfulProviderHttpStatuses(int status) throws Exception {
    REPLY.set(new Reply(status, SUCCESS, 0));
    assertEquals(status, post(FIXTURE, HEADERS).statusCode());
  }

  @ParameterizedTest
  @MethodSource("invalidHeaders")
  void rejectsMissingAndBlankHeadersWithoutCallingTheProducer(String name, String value) throws Exception {
    Map<String, String> headers = new HashMap<>(HEADERS);
    if (value == null) headers.remove(name); else headers.put(name, value);
    assertEquals(400, post(FIXTURE, headers).statusCode());
    assertEquals(0, CALLS.get());
  }

  static Stream<Arguments> invalidHeaders() {
    return HEADERS.keySet().stream().flatMap(name -> Stream.of(Arguments.of(name, null), Arguments.of(name, " ")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "unknown", "invalid-date", "without-zone", "empty-detail", "malformed"})
  void rejectsInvalidPayloadsWithoutCallingTheProducerOrLeakingTheirContents(String kind) throws Exception {
    ObjectNode body = (ObjectNode) mapper.readTree(FIXTURE);
    switch (kind) {
      case "missing" -> body.remove("account_name");
      case "unknown" -> body.put("unexpected", "private-input");
      case "invalid-date" -> body.put("event_timestamp", "private-input");
      case "without-zone" -> body.put("event_timestamp", "2026-11-13T11:00:00");
      case "empty-detail" -> ((ObjectNode) body.path("event_message").path("channel_payload_detail").get(0)).put("key", "");
      default -> { }
    }
    String json = kind.equals("malformed") ? "{private-input" : mapper.writeValueAsString(body);
    HttpResponse<String> response = post(json, HEADERS);
    assertEquals(400, response.statusCode(), response.body());
    assertFalse(response.body().contains("private-input"));
    assertEquals(0, CALLS.get());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 409, 429, 500, 503})
  void preservesRejectionsMapsServerFailuresAndDoesNotRetry(int upstreamStatus) throws Exception {
    REPLY.set(new Reply(upstreamStatus, "{\"secret\":\"private-provider-error\"}", 0));
    HttpResponse<String> response = post(FIXTURE, HEADERS);
    assertEquals(upstreamStatus < 500 ? upstreamStatus : 502, response.statusCode());
    assertEquals(upstreamStatus, mapper.readTree(response.body()).path("upstream_status").asInt());
    assertFalse(response.body().contains("private-provider-error"));
    assertEquals(1, CALLS.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "{}", "{\"data\":[]}", "{\"data\":[{\"status\":\"\"}]}", "invalid-json"})
  void rejectsEmptyAndInvalidSuccessfulResponses(String body) throws Exception {
    REPLY.set(new Reply(200, body, 0));
    HttpResponse<String> response = post(FIXTURE, HEADERS);
    assertEquals(502, response.statusCode());
    assertEquals(200, mapper.readTree(response.body()).path("upstream_status").asInt());
    assertEquals(1, CALLS.get());
  }

  @Test
  void rejectsRedirectsWithoutFollowingThem() throws Exception {
    REPLY.set(new Reply(302, SUCCESS, 0));
    assertEquals(502, post(FIXTURE, HEADERS).statusCode());
    assertEquals(1, CALLS.get());
  }

  @Test
  void reportsTimeoutWithoutRetryingThePost() throws Exception {
    REPLY.set(new Reply(200, SUCCESS, 1600));
    assertEquals(504, post(FIXTURE, HEADERS).statusCode());
    assertEquals(1, CALLS.get());
  }

  @Test
  void reportsDisconnectedProducerWithoutRetrying() throws Exception {
    REPLY.set(new Reply(0, "", 0));
    assertEquals(503, post(FIXTURE, HEADERS).statusCode());
    assertEquals(1, CALLS.get());
  }

  @Test
  void servesAngularAlongsideTheSubmissionApi() throws Exception {
    HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base() + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<app-root>"));
  }

  private HttpResponse<String> post(String body, Map<String, String> headers) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base() + PATH)).timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String base() { return "http://127.0.0.1:" + environment.getProperty("local.server.port"); }

  private static String fixture() {
    try (var stream = AuditSubmissionIntegrationTest.class.getResourceAsStream("/audit-request.json")) {
      return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
  }

  private static HttpServer upstream() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      server.createContext(PATH, exchange -> {
        Reply reply = REPLY.get();
        Map<String, String> headers = new HashMap<>();
        HEADERS.keySet().forEach(name -> headers.put(name, exchange.getRequestHeaders().getFirst(name)));
        RECORDED.set(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        CALLS.incrementAndGet();
        try {
          if (reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
          if (reply.status() == 0) return;
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          if (reply.status() == 302) exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + PATH);
          byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        finally { exchange.close(); }
      });
      server.start();
      return server;
    } catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
  }

  private record Reply(int status, String body, long delayMillis) {}
  private record Recorded(String method, String path, Map<String, String> headers, String body) {}
}
