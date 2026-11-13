package pe.com.scotiabank.tapp.auditquery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import jakarta.validation.Validator;
import java.net.*;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.adapter.out.http.AuditApiClient;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class SenderEdgeCasesUnitTest {
  private static final URI URL = URI.create("http://producer.invalid/log");
  private final AuditWebMapper webMapper = Mappers.getMapper(AuditWebMapper.class);

  @Test
  void preservesTheRemainingHeaderPositionsWhenTheLeadingHeadersAreAbsent() {
    assertEquals(new AuditContext(null, null, "TAPP", null), webMapper.toContext(null, null, "TAPP", null));
    assertEquals(new AuditContext(null, null, null, "PE"), webMapper.toContext(null, null, null, "PE"));
  }

  @Test
  void handlesUnregisteredProviderHttpStatusesWithoutLosingTheStatusOrCause() throws Exception {
    AuditApiClient client = client(request -> Mono.just(ClientResponse.create(HttpStatusCode.valueOf(599))
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("private-provider-body").build()));
    StepVerifier.create(client.send(event(), new AuditContext("v1", "WEB", "TAPP", "PE"))).expectErrorSatisfies(error -> {
      AuditDeliveryException failure = assertInstanceOf(AuditDeliveryException.class, error);
      assertEquals(FailureKind.PROVIDER_FAILURE, failure.kind());
      assertEquals(599, failure.providerStatusCode());
      WebClientResponseException cause = assertInstanceOf(WebClientResponseException.class, failure.getCause());
      assertEquals("Upstream HTTP error", cause.getStatusText());
      assertEquals("private-provider-body", cause.getResponseBodyAsString());
      assertFalse(failure.getMessage().contains("private-provider-body"));
    }).verify(Duration.ofSeconds(2));
  }

  @Test
  void recognizesSocketAndGenericTimeoutsEvenInsideRequestFailureCauses() throws Exception {
    for (Throwable cause : new Throwable[]{new SocketTimeoutException("socket deadline"), new TimeoutException("deadline")}) {
      WebClientRequestException requestFailure = new WebClientRequestException(cause, HttpMethod.POST, URL, HttpHeaders.EMPTY);
      AuditApiClient client = client(request -> Mono.error(requestFailure));
      StepVerifier.create(client.send(event(), new AuditContext("v1", "WEB", "TAPP", "PE"))).expectErrorSatisfies(error -> {
        AuditDeliveryException failure = assertInstanceOf(AuditDeliveryException.class, error);
        assertEquals(FailureKind.TIMEOUT, failure.kind());
        assertSame(requestFailure, failure.getCause());
      }).verify(Duration.ofSeconds(2));
    }
  }

  private AuditEvent event() throws Exception {
    try (var stream = getClass().getResourceAsStream("/audit-request.json")) {
      return webMapper.toDomain(JsonMapper.builder().build().readValue(stream, AuditLogRequest.class));
    }
  }

  private static AuditApiClient client(ExchangeFunction exchange) {
    return new AuditApiClient(WebClient.builder().exchangeFunction(exchange).build(),
        new AuditApiProperties(URL, Duration.ofSeconds(1), Duration.ofSeconds(1)), mock(Validator.class),
        Mappers.getMapper(AuditApiMapper.class));
  }
}
