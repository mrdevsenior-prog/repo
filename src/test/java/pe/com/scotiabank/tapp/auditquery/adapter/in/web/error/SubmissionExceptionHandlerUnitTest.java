package pe.com.scotiabank.tapp.auditquery.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;

class SubmissionExceptionHandlerUnitTest {
  private final AuditSubmissionExceptionHandler handler = new AuditSubmissionExceptionHandler();

  @ParameterizedTest
  @MethodSource("failures")
  void mapsEachFailureWithoutExposingTheProviderCause(FailureKind kind, Integer upstream, int expected) {
    var response = handler.delivery(new AuditDeliveryException(kind, "Mensaje público", upstream,
        new IllegalStateException("private-provider-data")));
    assertEquals(expected, response.getStatusCode().value());
    ProblemDetail body = (ProblemDetail) response.getBody();
    assertEquals("Mensaje público", body.getDetail());
    if (upstream != null) assertEquals(upstream, body.getProperties().get("upstream_status"));
    else assertNull(body.getProperties());
    assertFalse(body.toString().contains("private-provider-data"));
  }

  static Stream<Arguments> failures() {
    return Stream.of(Arguments.of(FailureKind.REJECTED, 403, 403), Arguments.of(FailureKind.REJECTED, null, 502),
        Arguments.of(FailureKind.PROVIDER_FAILURE, 503, 502), Arguments.of(FailureKind.INVALID_RESPONSE, 200, 502),
        Arguments.of(FailureKind.UNAVAILABLE, null, 503), Arguments.of(FailureKind.TIMEOUT, null, 504));
  }

  @Test
  void invalidJsonBodyAndHeaderValidationHaveSanitizedBadRequestBodies() {
    WebRequest request = mock(WebRequest.class);
    var unreadable = handler.handleHttpMessageNotReadable(
        new HttpMessageNotReadableException("private-input", mock(HttpInputMessage.class)),
        HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);
    var invalidBody = handler.handleMethodArgumentNotValid(mock(MethodArgumentNotValidException.class),
        HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);
    var invalidHeader = handler.handleHandlerMethodValidationException(mock(HandlerMethodValidationException.class),
        HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, request);
    for (var response : java.util.List.of(unreadable, invalidBody, invalidHeader)) {
      assertEquals(400, response.getStatusCode().value());
      assertFalse(response.getBody().toString().contains("private-input"));
      assertInstanceOf(ProblemDetail.class, response.getBody());
    }
  }
}
