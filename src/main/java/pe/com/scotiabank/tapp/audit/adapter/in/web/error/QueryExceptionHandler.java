package pe.com.scotiabank.tapp.audit.adapter.in.web.error;

import com.mongodb.MongoException;
import java.util.NoSuchElementException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.com.scotiabank.tapp.audit.adapter.common.error.ApiError;
import pe.com.scotiabank.tapp.audit.adapter.in.web.AuditQueryController;

@RestControllerAdvice(assignableTypes = AuditQueryController.class)
public class QueryExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(new ApiError("INVALID_FILTER", exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> invalidType() {
    return ResponseEntity.badRequest().body(new ApiError("INVALID_FILTER", "Revisa el formato de los filtros y la página."));
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ApiError> missing(NoSuchElementException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("EVENT_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler({MongoException.class, DataAccessException.class})
  ResponseEntity<ApiError> unavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiError("DATABASE_UNAVAILABLE", "No se pudo consultar MongoDB. Verifica la conexión e inténtalo nuevamente."));
  }
}
