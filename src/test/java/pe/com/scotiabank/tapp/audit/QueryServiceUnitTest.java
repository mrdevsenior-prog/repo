package pe.com.scotiabank.tapp.audit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pe.com.scotiabank.tapp.audit.application.port.out.AuditQueryPort;
import pe.com.scotiabank.tapp.audit.application.service.AuditQueryService;
import pe.com.scotiabank.tapp.audit.domain.*;

class QueryServiceUnitTest {
  private final AuditQueryPort port = mock(AuditQueryPort.class);
  private final AuditQueryService service = new AuditQueryService(port);
  private final AuditSearch search = new AuditSearch("", "", "", null, null, 0, 10, "timestamp", "desc");

  @Test
  void delegatesTheCompleteQueryWithoutChangingItsResults() {
    AuditPage page = new AuditPage(List.of(), 12, 0, 10);
    AuditSummary summary = new AuditSummary(12, 2, 3, 4);
    AuditCatalogs catalogs = new AuditCatalogs(List.of("TAPP"), List.of("HIGH"));
    AuditAnalytics analytics = new AuditAnalytics(summary, List.of(), List.of(), List.of(), List.of(), List.of(),
        0, "2026-11-01", "2026-11-13", "America/Lima", java.time.Instant.EPOCH);
    when(port.search(search)).thenReturn(page);
    when(port.summarize(search)).thenReturn(summary);
    when(port.analytics(search)).thenReturn(analytics);
    when(port.catalogs()).thenReturn(catalogs);
    assertSame(page, service.search(search));
    assertSame(summary, service.summarize(search));
    assertSame(analytics, service.analytics(search));
    assertSame(catalogs, service.catalogs());
    service.checkConnection();
    verify(port).checkConnection();
  }

  @Test
  void returnsTheWholeDocumentAndReportsAnUnknownUid() {
    Map<String, Object> document = Map.of("uid", "uid-1", "items", List.of(1, 2));
    when(port.findByUid("uid-1")).thenReturn(Optional.of(document));
    when(port.findByUid("missing")).thenReturn(Optional.empty());
    assertSame(document, service.detail("uid-1"));
    assertThrows(NoSuchElementException.class, () -> service.detail("missing"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejectsEmptyIdentifiersBeforeAccessingMongo(String uid) {
    assertThrows(IllegalArgumentException.class, () -> service.detail(uid));
    verifyNoInteractions(port);
  }

  @Test
  void boundsIdentifierLengthAndPropagatesStorageFailures() {
    assertThrows(IllegalArgumentException.class, () -> service.detail("u".repeat(201)));
    when(port.findByUid("u".repeat(200))).thenReturn(Optional.empty());
    assertThrows(NoSuchElementException.class, () -> service.detail("u".repeat(200)));
    IllegalStateException failure = new IllegalStateException("offline");
    when(port.search(search)).thenThrow(failure);
    assertSame(failure, assertThrows(IllegalStateException.class, () -> service.search(search)));
  }

  @Test
  void resultCollectionsCannotBeChangedThroughTheirOriginalListsOrAccessors() {
    ArrayList<String> values = new ArrayList<>(List.of("TAPP"));
    AuditCatalogs catalogs = new AuditCatalogs(values, values);
    ArrayList<AuditAnalytics.Bucket> buckets = new ArrayList<>(List.of(new AuditAnalytics.Bucket("day", 1)));
    AuditAnalytics analytics = new AuditAnalytics(new AuditSummary(1, 1, 0, 0), buckets, buckets, buckets,
        buckets, buckets, 0, "start", "end", "America/Lima", java.time.Instant.EPOCH);
    values.clear();
    buckets.clear();
    assertEquals(List.of("TAPP"), catalogs.applications());
    assertEquals(List.of("TAPP"), catalogs.priorities());
    for (var list : List.of(analytics.daily(), analytics.applications(), analytics.priorities(), analytics.actions(), analytics.hours())) {
      assertEquals(1, list.size());
      assertThrows(UnsupportedOperationException.class, list::clear);
    }
  }
}
