package pe.com.scotiabank.tapp.audit.adapter.in.web.error;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String FIELD_KEY = "field";
  private static final String MESSAGE_KEY = "message";

  @ExceptionHandler(AuditDeliveryException.class)
  public Mono<ResponseEntity<Object>> handleAuditApi(
      AuditDeliveryException exception, ServerWebExchange exchange) {
    HttpStatusCode status = resolveStatus(exception);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setTitle("Error al consumir la API de auditoría");
    if (exception.providerStatusCode() != null) {
      problem.setProperty("upstream_status", exception.providerStatusCode());
    }
    return handleExceptionInternal(exception, problem, HttpHeaders.EMPTY, status, exchange);
  }

  @Override
  protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
      WebExchangeBindException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      ServerWebExchange exchange) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "La solicitud contiene campos inválidos.");
    problem.setProperty(
        "errors",
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(FIELD_KEY, error.getField(), MESSAGE_KEY, "Campo requerido o inválido"))
            .toList());
    return handleExceptionInternal(exception, problem, headers, status, exchange);
  }

  @Override
  protected Mono<ResponseEntity<Object>> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      ServerWebExchange exchange) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, "La solicitud contiene campos inválidos.");
    problem.setProperty(
        "errors",
        exception.getParameterValidationResults().stream()
            .flatMap(
                result -> {
                  if (result instanceof ParameterErrors errors) {
                    return errors.getFieldErrors().stream()
                        .map(
                            error ->
                                Map.of(
                                    FIELD_KEY,
                                    error.getField(),
                                    MESSAGE_KEY,
                                    "Campo requerido o inválido"));
                  }
                  String name = result.getMethodParameter().getParameterName();
                  return Stream.of(
                      Map.of(
                          FIELD_KEY,
                          name == null ? "header" : name,
                          MESSAGE_KEY,
                          "Cabecera requerida o inválida"));
                })
            .toList());
    return handleExceptionInternal(exception, problem, headers, status, exchange);
  }

  @Override
  protected Mono<ResponseEntity<Object>> handleMissingRequestValueException(
      MissingRequestValueException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      ServerWebExchange exchange) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "La solicitud contiene campos inválidos.");
    problem.setProperty(
        "errors",
        List.of(
            Map.of(FIELD_KEY, exception.getName(), MESSAGE_KEY, "Cabecera requerida o inválida")));
    return handleExceptionInternal(exception, problem, headers, status, exchange);
  }

  @Override
  protected Mono<ResponseEntity<Object>> handleServerWebInputException(
      ServerWebInputException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      ServerWebExchange exchange) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "JSON inválido. Las fechas deben usar ISO 8601 con zona horaria.");
    return handleExceptionInternal(exception, problem, headers, status, exchange);
  }

  private static HttpStatusCode resolveStatus(AuditDeliveryException exception) {
    return switch (exception.kind()) {
      case REJECTED ->
          exception.providerStatusCode() == null
              ? HttpStatus.BAD_GATEWAY
              : HttpStatusCode.valueOf(exception.providerStatusCode());
      case PROVIDER_FAILURE, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
    };
  }
}
