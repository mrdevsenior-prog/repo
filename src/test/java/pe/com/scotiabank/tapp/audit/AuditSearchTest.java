package pe.com.scotiabank.tapp.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.com.scotiabank.tapp.audit.domain.AuditSearch;

class AuditSearchTest {
  @ParameterizedTest
  @CsvSource({"-1,10", "10001,10", "0,0", "0,101"})
  void rejectsInvalidPagination(int page, int size) {
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "", "", null, null, page, size, "timestamp", "desc"));
  }

  @Test
  void rejectsUnexpectedSortFieldsAndDirections() {
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "", "", null, null, 0, 10, "$where", "desc"));
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "", "", null, null, 0, 10, "timestamp", "drop"));
  }

  @Test
  void rejectsReversedOrEmptyDateIntervals() {
    Instant day = Instant.parse("2026-11-13T00:00:00Z");
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "", "", day, day, 0, 10, "timestamp", "desc"));
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "", "", day.plusSeconds(1), day, 0, 10, "timestamp", "desc"));
  }

  @Test
  void boundsAndNormalizesUserInput() {
    AuditSearch search = new AuditSearch("  persona  ", null, " HIGH ", null, null, 0, 10, "timestamp", "desc");
    assertAll(() -> assertEquals("persona", search.q()), () -> assertEquals("", search.application()),
        () -> assertEquals("HIGH", search.priority()));
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("x".repeat(121), "", "", null, null, 0, 10, "timestamp", "desc"));
  }

  @Test
  void validatesEachTextLimitAndAcceptsBoundaryValuesAndOpenDateIntervals() {
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "a".repeat(41), "", null, null, 0, 10, "timestamp", "desc"));
    assertThrows(IllegalArgumentException.class,
        () -> new AuditSearch("", "", "p".repeat(41), null, null, 0, 10, "timestamp", "desc"));
    Instant now = Instant.parse("2026-11-13T00:00:00Z");
    assertDoesNotThrow(() -> new AuditSearch("q".repeat(120), "a".repeat(40), "p".repeat(40), now,
        now.plusSeconds(1), 10000, 100, "priority", "asc"));
    assertDoesNotThrow(() -> new AuditSearch(null, null, null, now, null, 0, 1, "account", "desc"));
    assertDoesNotThrow(() -> new AuditSearch(null, null, null, null, now, 0, 1, "application", "desc"));
  }
}
