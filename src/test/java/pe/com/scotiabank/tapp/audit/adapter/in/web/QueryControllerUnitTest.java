package pe.com.scotiabank.tapp.audit.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import pe.com.scotiabank.tapp.audit.application.port.in.AuditQueryUseCase;
import pe.com.scotiabank.tapp.audit.domain.*;

class QueryControllerUnitTest {
  private final AuditQueryUseCase useCase = mock(AuditQueryUseCase.class);
  private final AuditQueryController controller = new AuditQueryController(useCase);

  @Test
  void forwardsEverySearchParameterAndNormalizesOffsetDates() {
    AuditPage page = new AuditPage(List.of(), 0, 3, 20);
    when(useCase.search(any())).thenReturn(page);
    assertSame(page, controller.search(" uid ", "TAPP", "HIGH", "2026-11-13T00:00:00-05:00",
        "2026-11-14T00:00:00-05:00", 3, 20, "account", "asc"));
    ArgumentCaptor<AuditSearch> query = ArgumentCaptor.forClass(AuditSearch.class);
    verify(useCase).search(query.capture());
    assertEquals(new AuditSearch("uid", "TAPP", "HIGH", Instant.parse("2026-11-13T05:00:00Z"),
        Instant.parse("2026-11-14T05:00:00Z"), 3, 20, "account", "asc"), query.getValue());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void supportsOptionalDatesWithoutSupplyingAnArtificialInterval(String date) {
    controller.search("", "", "", date, date, 0, 10, "timestamp", "desc");
    ArgumentCaptor<AuditSearch> query = ArgumentCaptor.forClass(AuditSearch.class);
    verify(useCase).search(query.capture());
    assertNull(query.getValue().from());
    assertNull(query.getValue().to());
  }

  @Test
  void usesTheSameFiltersForTheSummaryAndAnalytics() {
    AuditSummary summary = new AuditSummary(1, 1, 0, 0);
    AuditAnalytics analytics = new AuditAnalytics(summary, List.of(), List.of(), List.of(), List.of(), List.of(),
        0, "a", "b", "America/Lima", Instant.EPOCH);
    when(useCase.summarize(any())).thenReturn(summary);
    when(useCase.analytics(any())).thenReturn(analytics);
    assertSame(summary, controller.summary("q", "TAPP", "HIGH", null, null));
    assertSame(analytics, controller.analytics("q", "TAPP", "HIGH", null, null));
    AuditSearch expected = new AuditSearch("q", "TAPP", "HIGH", null, null, 0, 10, "timestamp", "desc");
    verify(useCase).summarize(expected);
    verify(useCase).analytics(expected);
  }

  @Test
  void returnsDetailsCatalogsAndTheResultOfAConnectionCheck() {
    Map<String, Object> detail = Map.of("uid", "u1");
    AuditCatalogs catalogs = new AuditCatalogs(List.of("TAPP"), List.of("HIGH"));
    when(useCase.detail("u1")).thenReturn(detail);
    when(useCase.catalogs()).thenReturn(catalogs);
    assertSame(detail, controller.detail("u1"));
    assertSame(catalogs, controller.catalogs());
    assertEquals(Map.of("status", "UP"), controller.health());
    verify(useCase).checkConnection();
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "2026-11-13T00:00:00"})
  void rejectsDatesWithoutAnExplicitTimezoneBeforeCallingTheUseCase(String date) {
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> controller.summary("", "", "", date, null));
    assertTrue(error.getMessage().contains("zona horaria"));
    verifyNoInteractions(useCase);
  }
}
