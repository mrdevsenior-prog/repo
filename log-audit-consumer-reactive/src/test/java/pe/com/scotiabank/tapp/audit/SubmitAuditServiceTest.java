package pe.com.scotiabank.tapp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.service.SubmitAuditService;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SubmitAuditServiceTest {

  private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(1);
  private static final AuditEvent EVENT = event();
  private static final AuditContext CONTEXT = new AuditContext("1", "mobile", "TAPP", "PE");
  private static final AuditSubmission SUBMISSION =
      new AuditSubmission(new AuditResult(List.of(new AuditResult.AuditStatus("OK"))), 201);

  @Test
  void doesNotCallOutputPortBeforeSubscription() {
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              return Mono.just(SUBMISSION);
            });

    Mono<AuditSubmission> pending = service.submit(EVENT, CONTEXT);

    assertThat(tuple(pending != null, calls.get())).isEqualTo(tuple(true, 0));
  }

  @Test
  void passesSameEventAndContextToPortAndReturnsExactSubmission() {
    AtomicReference<AuditEvent> sentEvent = new AtomicReference<>();
    AtomicReference<AuditContext> sentContext = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              sentEvent.set(event);
              sentContext.set(context);
              calls.incrementAndGet();
              return Mono.just(SUBMISSION);
            });

    StepVerifier.create(service.submit(EVENT, CONTEXT))
        .expectNextMatches(
            result ->
                result == SUBMISSION
                    && sentEvent.get() == EVENT
                    && sentContext.get() == CONTEXT
                    && calls.get() == 1)
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void propagatesReactivePortErrorWithoutSwallowingOrRetrying() {
    IllegalStateException failure = new IllegalStateException("outbound-failed");
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              return Mono.error(failure);
            });

    StepVerifier.create(service.submit(EVENT, CONTEXT))
        .expectErrorSatisfies(
            error -> assertThat(error).isSameAs(failure).matches(ignored -> calls.get() == 1))
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void convertsSynchronousPortFailureIntoReactiveErrorWithoutRetrying() {
    IllegalStateException failure = new IllegalStateException("port-failed-before-returning");
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              throw failure;
            });

    Mono<AuditSubmission> pending = service.submit(EVENT, CONTEXT);
    int callsBeforeSubscription = calls.get();
    StepVerifier.create(pending)
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isSameAs(failure)
                    .matches(ignored -> callsBeforeSubscription == 0 && calls.get() == 1))
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void rejectsNullEventOnSubscriptionWithoutCallingPort() {
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              return Mono.just(SUBMISSION);
            });

    Mono<AuditSubmission> pending = service.submit(null, CONTEXT);

    int callsBeforeSubscription = calls.get();
    StepVerifier.create(pending)
        .expectErrorMatches(
            error ->
                error instanceof NullPointerException
                    && "El evento es requerido".equals(error.getMessage())
                    && callsBeforeSubscription == 0
                    && calls.get() == 0)
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void rejectsNullContextOnSubscriptionWithoutCallingPort() {
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              return Mono.just(SUBMISSION);
            });

    Mono<AuditSubmission> pending = service.submit(EVENT, null);

    int callsBeforeSubscription = calls.get();
    StepVerifier.create(pending)
        .expectErrorMatches(
            error ->
                error instanceof NullPointerException
                    && "El contexto es requerido".equals(error.getMessage())
                    && callsBeforeSubscription == 0
                    && calls.get() == 0)
        .verify(VERIFY_TIMEOUT);
  }

  @Test
  void rejectsNullPortWhenConstructingService() {
    assertThrows(NullPointerException.class, () -> new SubmitAuditService(null));
  }

  @Test
  void propagatesCancellationToPendingOutputPortPublisher() {
    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              return Mono.<AuditSubmission>never().doOnCancel(() -> cancelled.set(true));
            });

    Disposable subscription = service.submit(EVENT, CONTEXT).subscribe();
    int callsAtSubscription = calls.get();
    subscription.dispose();

    assertThat(tuple(cancelled.get(), callsAtSubscription, calls.get()))
        .isEqualTo(tuple(true, 1, 1));
  }

  @Test
  void processesEachNewSubscriptionExactlyOnce() {
    AtomicInteger calls = new AtomicInteger();
    SubmitAuditService service =
        new SubmitAuditService(
            (event, context) -> {
              calls.incrementAndGet();
              return Mono.just(SUBMISSION);
            });
    Mono<AuditSubmission> pending = service.submit(EVENT, CONTEXT);

    StepVerifier.create(pending.concatWith(pending))
        .expectNextMatches(result -> result == SUBMISSION && calls.get() == 1)
        .expectNextMatches(result -> result == SUBMISSION && calls.get() == 2)
        .expectComplete()
        .verify(VERIFY_TIMEOUT);
  }

  private static AuditEvent event() {
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-09T15:19:58-05:00");
    AuditEvent.EventMessage message =
        new AuditEvent.EventMessage(
            "TAPP",
            "TAPP",
            "LOGIN",
            "Login",
            "DNI",
            "00000000",
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
        "TERMINATED",
        "demo",
        "source-service",
        InetAddress.getLoopbackAddress().getHostAddress(),
        "destination-service",
        InetAddress.getLoopbackAddress().getHostAddress(),
        "session",
        "EXECUTE",
        message);
  }
}
