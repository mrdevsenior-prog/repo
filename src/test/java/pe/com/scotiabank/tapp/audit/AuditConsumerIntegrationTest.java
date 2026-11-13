package pe.com.scotiabank.tapp.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.netty.channel.ChannelOption;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.reactor.netty.NettyWebServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.adapter.out.http.AuditApiClient;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(
    classes = AuditApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.health.mongo.enabled=false",
      "spring.mongodb.uri=mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=100&connectTimeoutMS=100"
    })
class AuditConsumerIntegrationTest {

  private static final String PATH = "/api/v1/audit/security/log";
  private static final String LOCAL_URL_PREFIX = "http://localhost:";
  private static final String SUCCESS = "{\"data\":[{\"status\":\"OK\"}]}";
  private static final String API_VERSION_HEADER = "x-api-version";
  private static final String CHANNEL_ID_HEADER = "x-channel-id";
  private static final String ORIGINATING_APPLICATION_HEADER = "x-originating-appl-code";
  private static final String COUNTRY_HEADER = "x-country-code";
  private static final String API_VERSION = "apiversion";
  private static final String CHANNEL_ID = "channel";
  private static final String ORIGINATING_APPLICATION = "originating";
  private static final String COUNTRY = "country";
  private static final String PROVIDER_STATUS_FIELD = "upstream_status";
  private static final String PRIVATE_UPSTREAM_DATA = "private-upstream-data";
  private static final String PRIVATE_INBOUND_DATA = "private-inbound-data";
  private static final String PROVIDER_ERROR =
      "{\"field\":\"event_message.app_code\",\"secret\":\"" + PRIVATE_UPSTREAM_DATA + "\"}";
  private static final String EVENT_MESSAGE = "event_message";
  private static final String CHANNEL_HEADER = "channel_payload_header";
  private static final String APPLICATION_CODE = "app_code";
  private static final String CASE_DISPLAY_NAME = "{0}: {1}";
  private static final int NO_CONTENT_STATUS = 204;
  private static final int RESPONSE_LIMIT_BYTES = 256 * 1024;
  private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);
  private static final System.Logger LOGGER =
      System.getLogger(AuditConsumerIntegrationTest.class.getName());
  private static final Map<String, String> HEADERS =
      Map.of(
          API_VERSION_HEADER, API_VERSION,
          CHANNEL_ID_HEADER, CHANNEL_ID,
          ORIGINATING_APPLICATION_HEADER, ORIGINATING_APPLICATION,
          COUNTRY_HEADER, COUNTRY);
  private static final BlockingQueue<StubResponse> RESPONSES = new LinkedBlockingQueue<>();
  private static final AtomicReference<RecordedRequest> RECORDED = new AtomicReference<>();
  private static final AtomicInteger CALLS = new AtomicInteger();
  private static final AtomicInteger ACTIVE_RESPONSES = new AtomicInteger();
  private static final Scheduler RESPONSE_SCHEDULER = Schedulers.newSingle("audit-test-responses");
  private static final HttpServer UPSTREAM = startUpstream();
  private static final String FIXTURE = loadFixture();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int port;

  @Autowired private JsonMapper mapper;
  @Autowired private Validator validator;
  @Autowired private WebClient.Builder webClientBuilder;
  @Autowired private ReactiveWebServerApplicationContext context;
  @Autowired private AuditApiClient auditApiClient;
  @Autowired private AuditWebMapper webMapper;
  @Autowired private AuditApiMapper apiMapper;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("audit.api.url", () -> LOCAL_URL_PREFIX + UPSTREAM.getAddress().getPort() + PATH);
    registry.add("audit.api.connect-timeout", () -> "1s");
    registry.add("audit.api.read-timeout", () -> "1s");
  }

  @BeforeEach
  void reset() {
    RESPONSES.clear();
    RECORDED.set(null);
    CALLS.set(0);
  }

  @AfterEach
  void awaitResponseCleanup() {
    Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> ACTIVE_RESPONSES.get() == 0);
  }

  @AfterAll
  static void shutdown() {
    UPSTREAM.stop(0);
    RESPONSE_SCHEDULER.dispose();
  }

  @ParameterizedTest
  @EnumSource(ForwardingClaim.class)
  void forwardsFullPayloadHeadersAndOriginalTimezone(ForwardingClaim claim) throws Exception {
    HttpResponse<String> response = send(FIXTURE, HEADERS);
    RecordedRequest request = RECORDED.get();
    assertThat(claim.observed(response, request, mapper)).isEqualTo(claim.expected(mapper));
  }

  @Test
  void preservesSuccessfulUpstreamStatus() throws Exception {
    RESPONSES.add(response(201, SUCCESS));
    assertThat(send(FIXTURE, HEADERS).statusCode()).isEqualTo(201);
  }

  @Test
  void runsOnReactiveNettyServer() {
    assertThat(context.getWebServer()).isInstanceOf(NettyWebServer.class);
  }

  @Test
  void exposesConfiguredWebClientBean() {
    assertThat(context.getBeansOfType(WebClient.class)).containsKey("auditWebClient");
  }

  @Test
  void defersOutboundRequestUntilSubscription() {
    auditApiClient.send(event(), auditContext());
    assertThat(CALLS.get()).isZero();
  }

  @Test
  void emitsProviderStatusWhenSubscribed() {
    StepVerifier.create(auditApiClient.send(event(), auditContext()))
        .expectNextMatches(submission -> submission.providerStatusCode() == 200)
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void performsExactlyOneRequestWhenSubscribed() {
    StepVerifier.create(auditApiClient.send(event(), auditContext()).map(submission -> CALLS.get()))
        .expectNext(1)
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void acceptsDynamicHeaderObjectsAndArrays() throws Exception {
    ObjectNode request = dynamicRequest();
    assertThat(send(mapper.writeValueAsString(request), HEADERS).statusCode()).isEqualTo(200);
  }

  @Test
  void preservesDynamicHeaderObjectsAndArrays() throws Exception {
    ObjectNode request = dynamicRequest();
    send(mapper.writeValueAsString(request), HEADERS);
    assertThat(mapper.readTree(RECORDED.get().body())).isEqualTo(request);
  }

  @ParameterizedTest(name = CASE_DISPLAY_NAME)
  @MethodSource("invalidRequestCases")
  void validatesIncomingRequestBeforeCallingProvider(InvalidRequest request, ValidationClaim claim)
      throws Exception {
    HttpResponse<String> response = send(request.body(), request.headers());
    assertThat(claim.observed(response)).isEqualTo(claim.expected());
  }

  @ParameterizedTest(name = CASE_DISPLAY_NAME)
  @MethodSource("providerResponseCases")
  void handlesProviderResponsesWithoutRetryingOrExposingData(
      ProviderResponse scenario, ProviderClaim claim) throws Exception {
    RESPONSES.add(scenario.response());
    HttpResponse<String> response = send(FIXTURE, HEADERS);
    assertThat(claim.observed(response, mapper)).isEqualTo(claim.expected(scenario));
  }

  @ParameterizedTest
  @EnumSource(RejectionClaim.class)
  void retainsProviderRejectionForLocalDiagnosis(RejectionClaim claim) {
    RESPONSES.add(response(400, PROVIDER_ERROR));
    StepVerifier.create(auditApiClient.send(event(), auditContext()))
        .expectErrorSatisfies(
            failure -> assertThat(claim.observed(failure)).isEqualTo(claim.expected(false)))
        .verify(VERIFY_TIMEOUT);
  }

  @ParameterizedTest
  @EnumSource(RejectionClaim.class)
  void discardsOversizedRejectionBodyAndRetainsBufferLimitCause(RejectionClaim claim) {
    RESPONSES.add(response(400, oversizedBody()));
    StepVerifier.create(auditApiClient.send(event(), auditContext()))
        .expectErrorSatisfies(
            failure -> assertThat(claim.observed(failure)).isEqualTo(claim.expected(true)))
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsConnectionFailureToServiceUnavailable() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    AuditApiProperties properties =
        new AuditApiProperties(
            URI.create(LOCAL_URL_PREFIX + closedPort + PATH),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1));
    reactor.netty.http.client.HttpClient reactorClient =
        reactor.netty.http.client.HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .responseTimeout(properties.readTimeout());
    AuditApiClient client =
        new AuditApiClient(
            webClientBuilder
                .clone()
                .clientConnector(new ReactorClientHttpConnector(reactorClient))
                .build(),
            properties,
            validator,
            apiMapper);

    StepVerifier.create(client.send(event(), auditContext()))
        .expectErrorSatisfies(
            failure ->
                assertThat(failure)
                    .isInstanceOfSatisfying(
                        AuditDeliveryException.class,
                        exception ->
                            assertThat(exception.kind()).isEqualTo(FailureKind.UNAVAILABLE)))
        .verify(VERIFY_TIMEOUT);
  }

  private ObjectNode dynamicRequest() {
    ObjectNode request = (ObjectNode) mapper.readTree(FIXTURE);
    ((ObjectNode) request.get(EVENT_MESSAGE).get(CHANNEL_HEADER))
        .set("extra", mapper.readTree("{\"count\":7,\"enabled\":true,\"items\":[1,2]}"));
    return request;
  }

  private pe.com.scotiabank.tapp.audit.domain.model.AuditEvent event() {
    return webMapper.toDomain(mapper.readValue(FIXTURE, AuditLogRequest.class));
  }

  private static AuditContext auditContext() {
    return new AuditContext(API_VERSION, CHANNEL_ID, ORIGINATING_APPLICATION, COUNTRY);
  }

  private HttpResponse<String> send(String body, Map<String, String> headers)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(LOCAL_URL_PREFIX + port + PATH))
            .timeout(Duration.ofSeconds(8))
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    headers.forEach(request::header);
    return httpClient.send(
        request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private static Stream<Arguments> invalidRequestCases() {
    JsonMapper jsonMapper = JsonMapper.builder().build();
    Map<String, String> missingHeader = new HashMap<>(HEADERS);
    missingHeader.remove(CHANNEL_ID_HEADER);
    Map<String, String> blankHeader = new HashMap<>(HEADERS);
    blankHeader.put(CHANNEL_ID_HEADER, " ");
    ObjectNode missingCode = (ObjectNode) jsonMapper.readTree(FIXTURE);
    ((ObjectNode) missingCode.get(EVENT_MESSAGE)).remove(APPLICATION_CODE);
    ObjectNode missingTimezone = (ObjectNode) jsonMapper.readTree(FIXTURE);
    missingTimezone.put("event_timestamp", "2026-07-09T15:19:58");
    ObjectNode unknownField = (ObjectNode) jsonMapper.readTree(FIXTURE);
    unknownField.put("unexpected_field", PRIVATE_INBOUND_DATA);
    return Stream.of(
            new InvalidRequest("missing header", FIXTURE, missingHeader, false, false),
            new InvalidRequest("blank header", FIXTURE, blankHeader, false, false),
            new InvalidRequest(
                "nested required field",
                jsonMapper.writeValueAsString(missingCode),
                HEADERS,
                true,
                false),
            new InvalidRequest(
                "missing timezone",
                jsonMapper.writeValueAsString(missingTimezone),
                HEADERS,
                false,
                false),
            new InvalidRequest(
                "unknown field", jsonMapper.writeValueAsString(unknownField), HEADERS, false, true),
            new InvalidRequest("malformed JSON", "{\"account_name\":", HEADERS, false, false))
        .flatMap(
            input ->
                Stream.of(ValidationClaim.values())
                    .filter(claim -> claim.appliesTo(input))
                    .map(claim -> Arguments.of(input, claim)));
  }

  private static Stream<Arguments> providerResponseCases() {
    Stream<ProviderResponse> clientErrors =
        IntStream.of(400, 401, 403, 404, 409, 422, 429, 499)
            .mapToObj(
                status ->
                    new ProviderResponse(
                        "client error", response(status, PROVIDER_ERROR), status, status));
    Stream<ProviderResponse> oversized =
        IntStream.of(200, 400, 503)
            .mapToObj(
                status ->
                    new ProviderResponse(
                        "oversized body",
                        response(status, oversizedBody()),
                        status == 400 ? 400 : 502,
                        status));
    Stream<ProviderResponse> redirects =
        IntStream.of(301, 302, 303, 307, 308)
            .mapToObj(
                status -> new ProviderResponse("redirect", response(status, ""), 502, status));
    Stream<ProviderResponse> empty =
        IntStream.of(200, NO_CONTENT_STATUS)
            .mapToObj(
                status ->
                    new ProviderResponse("empty response", response(status, ""), 502, status));
    Stream<ProviderResponse> failures =
        Stream.of(
            new ProviderResponse("server error", response(503, "{\"message\":\"down\"}"), 502, 503),
            new ProviderResponse(
                "read timeout",
                new StubResponse(200, SUCCESS, MediaType.APPLICATION_JSON_VALUE, 1600),
                504,
                null));
    Stream<ProviderResponse> invalid =
        Stream.of(
                "{}",
                "{\"data\":[]}",
                "{\"data\":[null]}",
                "{\"data\":[{\"status\":\"\"}]}",
                "not-json")
            .map(body -> new ProviderResponse("invalid success", response(200, body), 502, 200));
    return Stream.of(clientErrors, oversized, redirects, empty, failures, invalid)
        .flatMap(scenarios -> scenarios)
        .flatMap(
            scenario ->
                Stream.of(ProviderClaim.values()).map(claim -> Arguments.of(scenario, claim)));
  }

  private static StubResponse response(int status, String body) {
    return new StubResponse(status, body, MediaType.APPLICATION_JSON_VALUE, 0);
  }

  private static String oversizedBody() {
    return "{\"data\":[{\"status\":\""
        + PRIVATE_UPSTREAM_DATA
        + "x".repeat(RESPONSE_LIMIT_BYTES)
        + "\"}]}";
  }

  private static HttpServer startUpstream() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(PATH, AuditConsumerIntegrationTest::handleUpstream);
      server.start();
      return server;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static void handleUpstream(HttpExchange exchange) throws IOException {
    CALLS.incrementAndGet();
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    RECORDED.set(
        new RecordedRequest(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            Map.copyOf(exchange.getRequestHeaders()),
            body));
    StubResponse scheduled = RESPONSES.poll();
    StubResponse plan = scheduled == null ? response(200, SUCCESS) : scheduled;
    ACTIVE_RESPONSES.incrementAndGet();
    if (plan.delayMillis() > 0) {
      Mono.delay(Duration.ofMillis(plan.delayMillis()), RESPONSE_SCHEDULER)
          .subscribe(ignored -> writeResponse(exchange, plan));
    } else {
      writeResponse(exchange, plan);
    }
  }

  private static void writeResponse(HttpExchange exchange, StubResponse plan) {
    try (HttpExchange outgoing = exchange) {
      byte[] bytes = plan.body().getBytes(StandardCharsets.UTF_8);
      outgoing.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, plan.contentType());
      if (plan.status() >= 300 && plan.status() < 400) {
        outgoing
            .getResponseHeaders()
            .set(
                HttpHeaders.LOCATION,
                LOCAL_URL_PREFIX + UPSTREAM.getAddress().getPort() + PATH + "?redirected=true");
      }
      outgoing.sendResponseHeaders(
          plan.status(), plan.status() == NO_CONTENT_STATUS ? -1 : bytes.length);
      if (plan.status() != NO_CONTENT_STATUS) {
        outgoing.getResponseBody().write(bytes);
      }
    } catch (IOException exception) {
      LOGGER.log(System.Logger.Level.DEBUG, "Local mock client disconnected", exception);
    } finally {
      ACTIVE_RESPONSES.decrementAndGet();
    }
  }

  private static String loadFixture() {
    try (InputStream input =
        AuditConsumerIntegrationTest.class.getResourceAsStream("/audit-request.json")) {
      if (input == null) {
        throw new IllegalStateException("Missing audit-request.json fixture");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private enum ForwardingClaim {
    STATUS,
    RESPONSE_BODY,
    CALL_COUNT,
    METHOD,
    PATH_VALUE,
    API_VERSION_VALUE,
    CHANNEL_ID_VALUE,
    ORIGINATING_APPLICATION_VALUE,
    COUNTRY_VALUE,
    JSON_CONTENT_TYPE,
    FULL_PAYLOAD,
    DETAIL_COUNT,
    ORIGINAL_TIMEZONE;

    Object observed(HttpResponse<String> response, RecordedRequest request, JsonMapper jsonMapper) {
      return switch (this) {
        case STATUS -> response.statusCode();
        case RESPONSE_BODY -> jsonMapper.readTree(response.body());
        case CALL_COUNT -> CALLS.get();
        case METHOD -> request.method();
        case PATH_VALUE -> request.path();
        case API_VERSION_VALUE -> request.header(API_VERSION_HEADER);
        case CHANNEL_ID_VALUE -> request.header(CHANNEL_ID_HEADER);
        case ORIGINATING_APPLICATION_VALUE -> request.header(ORIGINATING_APPLICATION_HEADER);
        case COUNTRY_VALUE -> request.header(COUNTRY_HEADER);
        case JSON_CONTENT_TYPE ->
            request.header(HttpHeaders.CONTENT_TYPE).startsWith(MediaType.APPLICATION_JSON_VALUE);
        case FULL_PAYLOAD -> jsonMapper.readTree(request.body());
        case DETAIL_COUNT ->
            jsonMapper
                .readTree(request.body())
                .get(EVENT_MESSAGE)
                .get("channel_payload_detail")
                .size();
        case ORIGINAL_TIMEZONE ->
            jsonMapper.readTree(request.body()).get("log_timestamp").stringValue();
      };
    }

    Object expected(JsonMapper jsonMapper) {
      return switch (this) {
        case STATUS -> 200;
        case RESPONSE_BODY -> jsonMapper.readTree(SUCCESS);
        case CALL_COUNT -> 1;
        case METHOD -> "POST";
        case PATH_VALUE -> PATH;
        case API_VERSION_VALUE -> API_VERSION;
        case CHANNEL_ID_VALUE -> CHANNEL_ID;
        case ORIGINATING_APPLICATION_VALUE -> ORIGINATING_APPLICATION;
        case COUNTRY_VALUE -> COUNTRY;
        case JSON_CONTENT_TYPE -> true;
        case FULL_PAYLOAD -> jsonMapper.readTree(FIXTURE);
        case DETAIL_COUNT -> 20;
        case ORIGINAL_TIMEZONE -> "2026-07-09T15:19:59-05:00";
      };
    }
  }

  private enum ValidationClaim {
    STATUS,
    NO_PROVIDER_CALL,
    NESTED_FIELD_DETAIL,
    NO_PRIVATE_DATA;

    boolean appliesTo(InvalidRequest request) {
      return switch (this) {
        case STATUS, NO_PROVIDER_CALL -> true;
        case NESTED_FIELD_DETAIL -> request.nestedField();
        case NO_PRIVATE_DATA -> request.privateData();
      };
    }

    Object observed(HttpResponse<String> response) {
      return switch (this) {
        case STATUS -> response.statusCode();
        case NO_PROVIDER_CALL -> CALLS.get();
        case NESTED_FIELD_DETAIL -> response.body().contains("eventMessage.appCode");
        case NO_PRIVATE_DATA -> response.body().contains(PRIVATE_INBOUND_DATA);
      };
    }

    Object expected() {
      return switch (this) {
        case STATUS -> 400;
        case NO_PROVIDER_CALL -> 0;
        case NESTED_FIELD_DETAIL -> true;
        case NO_PRIVATE_DATA -> false;
      };
    }
  }

  private enum ProviderClaim {
    STATUS,
    PROVIDER_STATUS,
    NO_PRIVATE_DATA,
    CALL_COUNT;

    Object observed(HttpResponse<String> response, JsonMapper jsonMapper) {
      return switch (this) {
        case STATUS -> response.statusCode();
        case PROVIDER_STATUS -> {
          JsonNode code = jsonMapper.readTree(response.body()).get(PROVIDER_STATUS_FIELD);
          yield code == null ? null : code.intValue();
        }
        case NO_PRIVATE_DATA -> response.body().contains(PRIVATE_UPSTREAM_DATA);
        case CALL_COUNT -> CALLS.get();
      };
    }

    Object expected(ProviderResponse scenario) {
      return switch (this) {
        case STATUS -> scenario.expectedStatus();
        case PROVIDER_STATUS -> scenario.expectedProviderStatus();
        case NO_PRIVATE_DATA -> false;
        case CALL_COUNT -> 1;
      };
    }
  }

  private enum RejectionClaim {
    EXCEPTION_TYPE,
    FAILURE_KIND,
    PROVIDER_STATUS,
    SANITIZED_MESSAGE,
    CAUSE_TYPE,
    CAUSE_STATUS,
    DIAGNOSTIC_BODY,
    NESTED_LIMIT_CAUSE,
    CALL_COUNT;

    Object observed(Throwable failure) {
      if (this == EXCEPTION_TYPE) {
        return failure.getClass();
      }
      AuditDeliveryException delivery = AuditDeliveryException.class.cast(failure);
      if (this == FAILURE_KIND) {
        return delivery.kind();
      }
      if (this == PROVIDER_STATUS) {
        return delivery.providerStatusCode();
      }
      if (this == SANITIZED_MESSAGE) {
        return delivery.getMessage().contains(PRIVATE_UPSTREAM_DATA);
      }
      if (this == CALL_COUNT) {
        return CALLS.get();
      }
      Throwable cause = delivery.getCause();
      if (this == CAUSE_TYPE) {
        return cause instanceof WebClientResponseException;
      }
      WebClientResponseException httpFailure = WebClientResponseException.class.cast(cause);
      return switch (this) {
        case CAUSE_STATUS -> httpFailure.getStatusCode().value();
        case DIAGNOSTIC_BODY -> httpFailure.getResponseBodyAsString();
        case NESTED_LIMIT_CAUSE -> httpFailure.getCause() instanceof DataBufferLimitException;
        default -> throw new IllegalStateException("Unsupported rejection claim: " + this);
      };
    }

    Object expected(boolean oversized) {
      return switch (this) {
        case EXCEPTION_TYPE -> AuditDeliveryException.class;
        case FAILURE_KIND -> FailureKind.REJECTED;
        case PROVIDER_STATUS, CAUSE_STATUS -> 400;
        case SANITIZED_MESSAGE -> false;
        case CAUSE_TYPE -> true;
        case DIAGNOSTIC_BODY -> oversized ? "" : PROVIDER_ERROR;
        case NESTED_LIMIT_CAUSE -> oversized;
        case CALL_COUNT -> 1;
      };
    }
  }

  private record StubResponse(int status, String body, String contentType, long delayMillis) {}

  private record InvalidRequest(
      String name,
      String body,
      Map<String, String> headers,
      boolean nestedField,
      boolean privateData) {
    @Override
    public String toString() {
      return name;
    }
  }

  private record ProviderResponse(
      String name, StubResponse response, int expectedStatus, Integer expectedProviderStatus) {
    @Override
    public String toString() {
      return name + " (" + response.status() + ")";
    }
  }

  private record RecordedRequest(
      String method, String path, Map<String, List<String>> headers, String body) {
    String header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
          .map(Map.Entry::getValue)
          .filter(values -> !values.isEmpty())
          .map(List::getFirst)
          .findFirst()
          .orElse("");
    }
  }
}
