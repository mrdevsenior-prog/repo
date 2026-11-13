package pe.com.scotiabank.tapp.auditquery.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class QueryExceptionHandlerUnitTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  @Test
  void invalidFiltersAndParameterTypesReturnUsefulBadRequests() {
    var invalid = handler.invalid(new IllegalArgumentException("Página inválida"));
    assertEquals(400, invalid.getStatusCode().value());
    assertEquals("INVALID_FILTER", invalid.getBody().code());
    assertEquals("Página inválida", invalid.getBody().message());
    var invalidType = handler.invalidType();
    assertEquals(400, invalidType.getStatusCode().value());
    assertEquals("INVALID_FILTER", invalidType.getBody().code());
  }

  @Test
  void unknownEventsReturnNotFoundAndDatabaseErrorsStaySanitized() {
    var missing = handler.missing(new NoSuchElementException("No encontrado"));
    assertEquals(404, missing.getStatusCode().value());
    assertEquals("EVENT_NOT_FOUND", missing.getBody().code());
    var unavailable = handler.unavailable();
    assertEquals(503, unavailable.getStatusCode().value());
    assertEquals("DATABASE_UNAVAILABLE", unavailable.getBody().code());
    assertFalse(unavailable.getBody().message().contains("mongodb://"));
  }
}
