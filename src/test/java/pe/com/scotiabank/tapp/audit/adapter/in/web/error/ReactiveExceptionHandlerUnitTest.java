package pe.com.scotiabank.tapp.audit.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebInputException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import reactor.test.StepVerifier;

class ReactiveExceptionHandlerUnitTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @ParameterizedTest
  @MethodSource("failures")
  void mapsEveryDeliveryFailureToItsPublicResponse(FailureKind kind, Integer upstream, int expected) {
    StepVerifier.create(handler.handleAuditApi(new AuditDeliveryException(kind, "Mensaje público", upstream,
        new IllegalStateException("private-provider-data")), exchange())).assertNext(response -> {
      assertEquals(expected, response.getStatusCode().value());
      ProblemDetail body = (ProblemDetail) response.getBody();
      assertEquals("Mensaje público", body.getDetail());
      if (upstream == null) assertNull(body.getProperties());
      else assertEquals(upstream, body.getProperties().get("upstream_status"));
      assertFalse(body.toString().contains("private-provider-data"));
    }).expectComplete().verify(Duration.ofSeconds(2));
  }

  static Stream<Arguments> failures() {
    return Stream.of(Arguments.of(FailureKind.REJECTED, 400, 400), Arguments.of(FailureKind.REJECTED, null, 502),
        Arguments.of(FailureKind.PROVIDER_FAILURE, 503, 502), Arguments.of(FailureKind.INVALID_RESPONSE, 200, 502),
        Arguments.of(FailureKind.UNAVAILABLE, null, 503), Arguments.of(FailureKind.TIMEOUT, null, 504));
  }

  @Test
  void fieldBindingFailuresExposeTheFieldAndHideItsRejectedValue() {
    BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(new FieldError("request", "account_name", "private-input"));
    var exception = new WebExchangeBindException(mock(MethodParameter.class), binding);
    StepVerifier.create(handler.handleWebExchangeBindException(exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, exchange()))
        .assertNext(response -> {
          ProblemDetail body = (ProblemDetail) response.getBody();
          assertEquals(400, response.getStatusCode().value());
          assertTrue(body.getProperties().get("errors").toString().contains("account_name"));
          assertFalse(body.toString().contains("private-input"));
        }).verifyComplete();
  }

  @Test
  void methodValidationHandlesFieldsNamedHeadersAndUnnamedParameters() {
    ParameterErrors fields = mock(ParameterErrors.class);
    when(fields.getFieldErrors()).thenReturn(List.of(new FieldError("request", "event_message", "private-input")));
    ParameterValidationResult named = mock(ParameterValidationResult.class);
    MethodParameter namedParameter = mock(MethodParameter.class);
    when(namedParameter.getParameterName()).thenReturn("apiVersion");
    when(named.getMethodParameter()).thenReturn(namedParameter);
    ParameterValidationResult unnamed = mock(ParameterValidationResult.class);
    when(unnamed.getMethodParameter()).thenReturn(mock(MethodParameter.class));
    HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);
    when(exception.getParameterValidationResults()).thenReturn(List.of(fields, named, unnamed));
    StepVerifier.create(handler.handleHandlerMethodValidationException(exception, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, exchange()))
        .assertNext(response -> {
          ProblemDetail body = (ProblemDetail) response.getBody();
          String errors = body.getProperties().get("errors").toString();
          assertTrue(errors.contains("event_message"));
          assertTrue(errors.contains("apiVersion"));
          assertTrue(errors.contains("header"));
          assertFalse(errors.contains("private-input"));
        }).verifyComplete();
  }

  @Test
  void missingHeadersAndMalformedJsonUseSanitizedErrorDetails() {
    MissingRequestValueException missing = mock(MissingRequestValueException.class);
    when(missing.getName()).thenReturn("x-channel-id");
    StepVerifier.create(handler.handleMissingRequestValueException(missing, HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, exchange()))
        .assertNext(response -> {
          assertEquals(400, response.getStatusCode().value());
          assertTrue(response.getBody().toString().contains("x-channel-id"));
        }).verifyComplete();
    StepVerifier.create(handler.handleServerWebInputException(new ServerWebInputException("private-input"),
        HttpHeaders.EMPTY, HttpStatus.BAD_REQUEST, exchange())).assertNext(response -> {
          assertEquals(400, response.getStatusCode().value());
          assertFalse(response.getBody().toString().contains("private-input"));
        }).verifyComplete();
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/audit/security/log").build());
  }
}
