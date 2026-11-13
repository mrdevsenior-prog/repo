package pe.com.scotiabank.tapp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaderConstants;
import pe.com.scotiabank.tapp.audit.adapter.out.http.AuditApiClient;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuditApiClientTest {

  private static final URI URL = URI.create("https://audit.invalid/api/v1/audit/security/log");
  private static final String SUCCESS = "{\"data\":[{\"status\":\"OK\"}]}";
  private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(3);
  private static final AuditContext CONTEXT = new AuditContext("v1", "channel", "TAPP", "PE");
  private static final AuditEvent EVENT = event();
  private static ValidatorFactory validatorFactory;

  @BeforeAll
  static void initializeValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @Test
  void defersEveryExchangeUntilSubscriptionAndCreatesColdPublisher() {
    AtomicInteger calls = new AtomicInteger();
    AuditApiClient client =
        client(
            request -> {
              calls.incrementAndGet();
              return Mono.just(response(200, SUCCESS));
            });
    Mono<AuditSubmission> pending = client.send(EVENT, CONTEXT);
    List<Integer> subscriptionCounts = new ArrayList<>();
    subscriptionCounts.add(calls.get());
    AuditSubmission expected =
        new AuditSubmission(new AuditResult(List.of(new AuditResult.AuditStatus("OK"))), 200);

    StepVerifier.create(pending.concatWith(pending))
        .consumeNextWith(
            submission -> {
              subscriptionCounts.add(calls.get());
              assertThat(submission).isEqualTo(expected);
            })
        .consumeNextWith(
            submission -> {
              subscriptionCounts.add(calls.get());
              assertThat(tuple(submission, subscriptionCounts))
                  .isEqualTo(tuple(expected, List.of(0, 1, 2)));
            })
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void sendsPostToConfiguredUrlWithAllFourHeadersAndJsonMediaTypes() {
    AuditApiClient client =
        client(
            request -> {
              assertThat(request.method()).isEqualTo(HttpMethod.POST);
              assertThat(request.url()).isEqualTo(URL);
              assertThat(request.headers().getFirst(AuditHttpHeaderConstants.API_VERSION))
                  .isEqualTo("v1");
              assertThat(request.headers().getFirst(AuditHttpHeaderConstants.CHANNEL_ID))
                  .isEqualTo("channel");
              assertThat(request.headers().getFirst(AuditHttpHeaderConstants.ORIGINATING_APPL_CODE))
                  .isEqualTo("TAPP");
              assertThat(request.headers().getFirst(AuditHttpHeaderConstants.COUNTRY_CODE))
                  .isEqualTo("PE");
              assertThat(request.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
              assertThat(request.headers().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
              return Mono.just(response(200, SUCCESS));
            });

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .consumeNextWith(
            submission ->
                assertThat(submission.result().data().getFirst().status()).isEqualTo("OK"))
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 201})
  void mapsSuccessfulResponseAndPreservesProviderStatus(int status) {
    AuditApiClient client = client(request -> Mono.just(response(status, SUCCESS)));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectNext(
            new AuditSubmission(
                new AuditResult(List.of(new AuditResult.AuditStatus("OK"))), status))
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 503})
  void preservesHttpRejectionCauseAndBodyForLocalDiagnosisWithoutRetrying(int status) {
    String providerBody = "{\"detail\":\"private-provider-data\"}";
    AtomicInteger calls = new AtomicInteger();
    AuditApiClient client =
        client(
            request -> {
              calls.incrementAndGet();
              return Mono.just(response(status, providerBody));
            });

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        AuditDeliveryException.class,
                        failure -> {
                          assertThat(failure.kind())
                              .isEqualTo(
                                  status < 500
                                      ? FailureKind.REJECTED
                                      : FailureKind.PROVIDER_FAILURE);
                          assertThat(failure.providerStatusCode()).isEqualTo(status);
                          assertThat(failure.getMessage()).doesNotContain("private-provider-data");
                          assertThat(failure.getCause())
                              .isInstanceOfSatisfying(
                                  WebClientResponseException.class,
                                  cause -> {
                                    assertThat(cause.getStatusCode().value()).isEqualTo(status);
                                    assertThat(cause.getResponseBodyAsString())
                                        .isEqualTo(providerBody);
                                    assertThat(calls).hasValue(1);
                                  });
                        }))
        .verify(VERIFY_TIMEOUT);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"data\":[]}",
        "{\"data\":[null]}",
        "{\"data\":[{\"status\":\" \"}]}",
        "not-json",
        "null"
      })
  void rejectsMalformedOrContractInvalidSuccessfulResponses(String body) {
    AuditApiClient client = client(request -> Mono.just(response(200, body)));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(error -> assertFailure(error, FailureKind.INVALID_RESPONSE, 200))
        .verify(VERIFY_TIMEOUT);
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 204})
  void rejectsEmptySuccessfulResponse(int status) {
    AuditApiClient client = client(request -> Mono.just(response(status, "")));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(error -> assertFailure(error, FailureKind.INVALID_RESPONSE, status))
        .verify(VERIFY_TIMEOUT);
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 302, 303, 307, 308})
  void rejectsRedirectResponseAndDoesNotPerformAnotherExchange(int status) {
    AtomicInteger calls = new AtomicInteger();
    AuditApiClient client =
        client(
            request -> {
              calls.incrementAndGet();
              return Mono.just(
                  ClientResponse.create(HttpStatusCode.valueOf(status))
                      .header(HttpHeaders.LOCATION, "https://redirect.invalid/other")
                      .body("")
                      .build());
            });

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> {
              assertFailure(error, FailureKind.INVALID_RESPONSE, status);
              assertThat(calls).hasValue(1);
            })
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsConnectionFailureToUnavailableAndRetainsCause() {
    WebClientRequestException cause =
        new WebClientRequestException(
            new ConnectException("connection refused"), HttpMethod.POST, URL, new HttpHeaders());
    AtomicInteger calls = new AtomicInteger();
    AuditApiClient client =
        client(
            request -> {
              calls.incrementAndGet();
              return Mono.error(cause);
            });

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> {
              assertFailure(error, FailureKind.UNAVAILABLE, null);
              assertThat(error).hasCauseReference(cause);
              assertThat(calls).hasValue(1);
            })
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsRawTimeoutToTimeoutAndRetainsCause() {
    TimeoutException cause = new TimeoutException("exchange deadline");
    AuditApiClient client = client(request -> Mono.error(cause));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> {
              assertFailure(error, FailureKind.TIMEOUT, null);
              assertThat(error).hasCauseReference(cause);
            })
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsWrappedReadTimeoutToTimeoutInsteadOfUnavailable() {
    WebClientRequestException cause =
        new WebClientRequestException(
            ReadTimeoutException.INSTANCE, HttpMethod.POST, URL, new HttpHeaders());
    AuditApiClient client = client(request -> Mono.error(cause));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> {
              assertFailure(error, FailureKind.TIMEOUT, null);
              assertThat(error).hasCauseReference(cause);
            })
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsWrappedConnectTimeoutToTimeoutInsteadOfUnavailable() {
    WebClientRequestException cause =
        new WebClientRequestException(
            new ConnectTimeoutException("connect deadline"),
            HttpMethod.POST,
            URL,
            new HttpHeaders());
    AuditApiClient client = client(request -> Mono.error(cause));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> {
              assertFailure(error, FailureKind.TIMEOUT, null);
              assertThat(error).hasCauseReference(cause);
            })
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsTimeoutWhileReadingSuccessfulBodyToTimeout() {
    AuditApiClient client =
        client(
            request ->
                Mono.just(
                    ClientResponse.create(HttpStatusCode.valueOf(200))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.error(ReadTimeoutException.INSTANCE))
                        .build()));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(error -> assertFailure(error, FailureKind.TIMEOUT, null))
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void mapsTimeoutWhileReadingRejectedBodyToTimeout() {
    AuditApiClient client =
        client(
            request ->
                Mono.just(
                    ClientResponse.create(HttpStatusCode.valueOf(400))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.error(ReadTimeoutException.INSTANCE))
                        .build()));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(error -> assertFailure(error, FailureKind.TIMEOUT, null))
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void discardsUnreadableRejectionBodyAndRetainsStatusAndBufferLimitCause() {
    DataBufferLimitException readFailure = new DataBufferLimitException("response limit");
    AuditApiClient client =
        client(
            request ->
                Mono.just(
                    ClientResponse.create(HttpStatusCode.valueOf(400))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(Flux.error(readFailure))
                        .build()));

    StepVerifier.create(client.send(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> {
              assertFailure(error, FailureKind.REJECTED, 400);
              assertThat(error.getCause())
                  .isInstanceOfSatisfying(
                      WebClientResponseException.class,
                      cause -> {
                        assertThat(cause.getResponseBodyAsByteArray()).isEmpty();
                        assertThat(cause).hasCauseReference(readFailure);
                      });
            })
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void propagatesCancellationToPendingOutboundExchange() {
    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicInteger calls = new AtomicInteger();
    AuditApiClient client =
        client(
            request -> {
              calls.incrementAndGet();
              return Mono.<ClientResponse>never().doOnCancel(() -> cancelled.set(true));
            });

    Disposable subscription = client.send(EVENT, CONTEXT).subscribe();
    subscription.dispose();

    assertThat(tuple(calls.get(), cancelled.get())).isEqualTo(tuple(1, true));
  }

  private static AuditApiClient client(ExchangeFunction exchange) {
    AuditApiProperties properties =
        new AuditApiProperties(URL, Duration.ofSeconds(1), Duration.ofSeconds(2));
    return new AuditApiClient(
        WebClient.builder().exchangeFunction(exchange).build(),
        properties,
        validatorFactory.getValidator(),
        Mappers.getMapper(AuditApiMapper.class));
  }

  private static ClientResponse response(int status, String body) {
    return ClientResponse.create(HttpStatusCode.valueOf(status))
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .build();
  }

  private static void assertFailure(Throwable error, FailureKind kind, Integer upstreamStatus) {
    assertThat(error)
        .isInstanceOfSatisfying(
            AuditDeliveryException.class,
            failure -> {
              assertThat(failure.kind()).isEqualTo(kind);
              assertThat(failure.providerStatusCode()).isEqualTo(upstreamStatus);
            });
  }

  private static AuditEvent event() {
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-09T15:19:58-05:00");
    String loopbackAddress = InetAddress.getLoopbackAddress().getHostAddress();
    AuditEvent.EventMessage message =
        new AuditEvent.EventMessage(
            "TAPP",
            "TAPP App",
            "LOGIN",
            "Login",
            "DNI",
            "12345678",
            "device",
            "operation",
            "cams",
            "account",
            Map.of("header", "Login"),
            List.of(new AuditEvent.PayloadDetail("result", "SUCCESS")));
    return new AuditEvent(
        timestamp,
        "AUDIT_LOG",
        timestamp,
        "INFORMATION",
        "MEDIUM",
        "SUCCESS",
        "test-account",
        "source",
        loopbackAddress,
        "destination",
        loopbackAddress,
        "session",
        "EXECUTE",
        message);
  }
}
