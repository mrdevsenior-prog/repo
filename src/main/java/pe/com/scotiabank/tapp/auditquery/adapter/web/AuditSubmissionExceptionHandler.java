package pe.com.scotiabank.tapp.auditquery.adapter.web;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;

@RestControllerAdvice(assignableTypes = AuditSubmissionController.class)
public class AuditSubmissionExceptionHandler extends ResponseEntityExceptionHandler {
  @ExceptionHandler(AuditDeliveryException.class)
  public ResponseEntity<Object> delivery(AuditDeliveryException exception) {
    HttpStatusCode status = switch (exception.kind()) {
      case REJECTED -> exception.providerStatusCode() == null ? HttpStatus.BAD_GATEWAY
          : HttpStatusCode.valueOf(exception.providerStatusCode());
      case PROVIDER_FAILURE, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
    };
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setTitle("Error al consumir la API de auditoría");
    if (exception.providerStatusCode() != null) problem.setProperty("upstream_status", exception.providerStatusCode());
    return ResponseEntity.status(status).body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return invalid(exception, headers, status, request, "JSON inválido. Las fechas deben usar ISO 8601 con zona horaria.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return invalid(exception, headers, status, request, "La solicitud contiene campos requeridos o inválidos.");
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return invalid(exception, headers, status, request, "La solicitud contiene campos o cabeceras requeridos o inválidos.");
  }

  private ResponseEntity<Object> invalid(Exception exception, HttpHeaders headers, HttpStatusCode status,
      WebRequest request, String message) {
    return handleExceptionInternal(exception, ProblemDetail.forStatusAndDetail(status, message), headers, status, request);
  }
}
