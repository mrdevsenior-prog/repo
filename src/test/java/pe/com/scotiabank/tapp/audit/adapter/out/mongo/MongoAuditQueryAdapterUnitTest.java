package pe.com.scotiabank.tapp.audit.adapter.out.mongo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mongodb.Function;
import com.mongodb.MongoException;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.CountOptions;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.bson.*;
import org.bson.conversions.Bson;
import org.bson.types.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import pe.com.scotiabank.tapp.audit.domain.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class MongoAuditQueryAdapterUnitTest {
  private final MongoTemplate mongo = mock(MongoTemplate.class);
  private final MongoCollection<Document> collection = mock(MongoCollection.class);
  private final FindIterable<Document> find = mock(FindIterable.class, RETURNS_SELF);
  private final AggregateIterable<Document> aggregate = mock(AggregateIterable.class, RETURNS_SELF);
  private final List<Document> rows = new ArrayList<>();
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-13T20:00:00Z"), ZoneId.of("America/Lima"));
  private final MongoAuditQueryAdapter adapter = new MongoAuditQueryAdapter(mongo, "events", clock);
  private Document aggregateResult;

  @BeforeEach
  void storageDoubles() {
    when(mongo.getCollection("events")).thenReturn(collection);
    when(collection.countDocuments(any(Bson.class), any(CountOptions.class))).thenAnswer(invocation -> (long) rows.size());
    when(collection.find(any(Bson.class))).thenReturn(find);
    when(find.first()).thenAnswer(invocation -> rows.isEmpty() ? null : rows.getFirst());
    when(find.map(any(Function.class))).thenAnswer(invocation -> {
      Function<Document, AuditEvent> mapping = invocation.getArgument(0);
      MongoIterable<AuditEvent> mapped = mock(MongoIterable.class);
      when(mapped.into(any(Collection.class))).thenAnswer(into -> {
        Collection<AuditEvent> output = into.getArgument(0);
        rows.forEach(row -> output.add(mapping.apply(row)));
        return output;
      });
      return mapped;
    });
    when(collection.aggregate(anyList())).thenReturn(aggregate);
    when(aggregate.first()).thenAnswer(invocation -> aggregateResult);
  }

  @Test
  void pagesAndProjectsTheStoredDocumentWithStableDescendingSortAndAQueryTimeout() {
    Instant timestamp = Instant.parse("2026-11-13T16:00:00Z");
    rows.add(new Document("uid", "u1").append("account_name", "user").append("event_timestamp", Date.from(timestamp))
        .append("audit_action", "READ").append("event_category", "DONE").append("event_priority", "HIGH")
        .append("event_type", "INFO").append("event_message", new Document("app_code", "TAPP")
            .append("operation_number", "OP-1").append("app_operation_description", "Consulta")));
    AuditPage page = adapter.search(search("", "", "", null, null, "desc"));
    assertEquals(1, page.total());
    assertEquals(new AuditEvent("u1", "user", "TAPP", "OP-1", "READ", "DONE", "HIGH", "INFO", timestamp, "Consulta"), page.items().getFirst());
    verify(find).skip(20);
    verify(find).limit(10);
    verify(find).sort(new Document("event_timestamp", -1).append("_id", -1));
    verify(find).maxTime(5, TimeUnit.SECONDS);
    ArgumentCaptor<Bson> projection = ArgumentCaptor.forClass(Bson.class);
    verify(find).projection(projection.capture());
    assertEquals(1, bson(projection.getValue()).getInt32("event_message.operation_number").getValue());
  }

  @Test
  void handlesMissingFieldsAndAllSupportedTimestampRepresentations() {
    rows.add(new Document());
    rows.add(new Document("event_timestamp", Instant.EPOCH));
    rows.add(new Document("event_timestamp", "legacy-date").append("account_name", 123));
    AuditPage page = adapter.search(search("", "", "", null, null, "asc"));
    assertEquals("", page.items().getFirst().description());
    assertNull(page.items().getFirst().eventTimestamp());
    assertEquals(Instant.EPOCH, page.items().get(1).eventTimestamp());
    assertNull(page.items().get(2).eventTimestamp());
    assertEquals("123", page.items().get(2).accountName());
    verify(find).sort(new Document("event_timestamp", 1).append("_id", 1));
  }

  @Test
  void combinesLiteralSearchApplicationPriorityAndExclusiveDateBounds() {
    Instant from = Instant.parse("2026-11-01T05:00:00Z");
    Instant to = Instant.parse("2026-11-14T05:00:00Z");
    adapter.search(search(".*[x]", "TAPP", "HIGH", from, to, "desc"));
    ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
    verify(collection).find(filter.capture());
    BsonArray conditions = bson(filter.getValue()).getArray("$and");
    assertEquals(5, conditions.size());
    BsonArray fields = conditions.get(0).asDocument().getArray("$or");
    assertEquals(6, fields.size());
    BsonRegularExpression regex = fields.get(0).asDocument().getRegularExpression("uid");
    assertEquals("\\Q.*[x]\\E", regex.getPattern());
    assertEquals("i", regex.getOptions());
    assertEquals("TAPP", conditions.get(1).asDocument().getString("event_message.app_code").getValue());
    assertEquals("HIGH", conditions.get(2).asDocument().getString("event_priority").getValue());
    assertEquals(from.toEpochMilli(), conditions.get(3).asDocument().getDocument("event_timestamp").getDateTime("$gte").getValue());
    assertEquals(to.toEpochMilli(), conditions.get(4).asDocument().getDocument("event_timestamp").getDateTime("$lt").getValue());
  }

  @Test
  void usesAnEmptyFilterForAnUnfilteredQuery() {
    adapter.search(search("", "", "", null, null, "desc"));
    ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
    verify(collection).find(filter.capture());
    assertTrue(bson(filter.getValue()).isEmpty());
  }

  @Test
  void returnsTheWholeDocumentWithRecursiveBsonConversionAndWithoutChangingTheOriginal() {
    ObjectId id = new ObjectId("000000000000000000000001");
    Date date = Date.from(Instant.EPOCH);
    Document nested = new Document("amount", Decimal128.parse("12.50")).append("nullable", null);
    Document document = new Document("_id", id).append("at", date).append("binary", new byte[]{1, 2, 3})
        .append("map", nested).append("list", Arrays.asList(nested, false, 0, "", null));
    rows.add(document);
    Map<String, Object> result = adapter.findByUid("u1").orElseThrow();
    assertEquals(id.toHexString(), result.get("_id"));
    assertEquals("1970-01-01T00:00:00Z", result.get("at"));
    assertEquals("AQID", result.get("binary"));
    Map<String, Object> map = (Map<String, Object>) result.get("map");
    assertEquals("12.50", map.get("amount"));
    assertTrue(map.containsKey("nullable"));
    assertNull(map.get("nullable"));
    assertEquals(Arrays.asList(map, false, 0, "", null), result.get("list"));
    assertInstanceOf(Decimal128.class, nested.get("amount"));
    verify(find).maxTime(5, TimeUnit.SECONDS);
  }

  @Test
  void returnsAnEmptyOptionalForAnUnknownDocument() { assertTrue(adapter.findByUid("missing").isEmpty()); }

  @Test
  void aggregatesSummaryTotalsAndUsesTheLimaCalendarDay() {
    aggregateResult = facets();
    assertEquals(new AuditSummary(7, 2, 3, 4), adapter.summarize(search("", "", "", null, null, "desc")));
    List<Document> pipeline = pipeline();
    Document facet = pipeline.get(1).get("$facet", Document.class);
    Document today = facet.getList("today", Document.class).getFirst().get("$match", Document.class).get("event_timestamp", Document.class);
    assertEquals(Instant.parse("2026-11-13T05:00:00Z"), today.getDate("$gte").toInstant());
    assertEquals(Instant.parse("2026-11-14T05:00:00Z"), today.getDate("$lt").toInstant());
    verify(aggregate).maxTime(5, TimeUnit.SECONDS);
  }

  @Test
  void emptyAndAbsentFacetsProduceZeroCounts() {
    assertEquals(new AuditSummary(0, 0, 0, 0), adapter.summarize(search("", "", "", null, null, "desc")));
    aggregateResult = new Document("total", List.of());
    assertEquals(new AuditSummary(0, 0, 0, 0), adapter.summarize(search("", "", "", null, null, "desc")));
  }

  @Test
  void computesAllAnalyticsSeriesAndUsesTheirActualDateRange() {
    aggregateResult = facets().append("daily", List.of(bucket("2026-11-10", 3), bucket("2026-11-12", 4)))
        .append("applications", List.of(bucket("TAPP", 7))).append("priorities", List.of(bucket("HIGH", 3)))
        .append("actions", List.of(bucket("READ", 7))).append("hours", List.of(bucket("11", 7)));
    AuditAnalytics result = adapter.analytics(search("", "", "", null, null, "desc"));
    assertEquals(new AuditSummary(7, 2, 3, 4), result.summary());
    assertEquals("2026-11-10", result.rangeStart());
    assertEquals("2026-11-12", result.rangeEnd());
    assertEquals(7, result.daily().stream().mapToLong(AuditAnalytics.Bucket::count).sum());
    assertEquals("TAPP", result.applications().getFirst().key());
    assertEquals("HIGH", result.priorities().getFirst().key());
    assertEquals("READ", result.actions().getFirst().key());
    assertEquals("11", result.hours().getFirst().key());
    assertEquals(clock.instant(), result.generatedAt());
    assertEquals("America/Lima", result.timezone());
    Document grouping = pipeline().get(1).get("$facet", Document.class).getList("daily", Document.class)
        .get(1).get("$group", Document.class).get("_id", Document.class).get("$dateToString", Document.class);
    assertEquals("America/Lima", grouping.getString("timezone"));
    assertEquals("%Y-%m-%d", grouping.getString("format"));
  }

  @Test
  void analyticsHandlesNullMissingAndEmptyFacetsAndNullCategoryKeys() {
    AuditAnalytics absent = adapter.analytics(search("", "", "", null, null, "desc"));
    assertEquals("2026-11-07", absent.rangeStart());
    assertEquals("2026-11-13", absent.rangeEnd());
    assertTrue(absent.daily().isEmpty());
    aggregateResult = new Document("daily", List.of()).append("priorities", List.of(bucket(null, 1)))
        .append("undated", List.of(new Document("value", 2)));
    AuditAnalytics result = adapter.analytics(search("", "", "", null, null, "desc"));
    assertEquals("Sin dato", result.priorities().getFirst().key());
    assertEquals(2, result.undated());
    assertTrue(result.applications().isEmpty());
  }

  @Test
  void analyticsHonorsRequestedDateBoundsAndExclusiveMidnight() {
    Instant from = Instant.parse("2026-11-01T05:00:00Z");
    Instant to = Instant.parse("2026-11-14T05:00:00Z");
    AuditAnalytics result = adapter.analytics(search("", "", "", from, to, "desc"));
    assertEquals("2026-11-01", result.rangeStart());
    assertEquals("2026-11-13", result.rangeEnd());
  }

  @Test
  void catalogsRemoveBlankValuesSortResultsAndReadTheRightStoredFields() {
    stubDistinct("event_message.app_code", List.of("TAPP", "", " ", "B3QX"));
    stubDistinct("event_priority", List.of("NORMAL", "HIGH", ""));
    AuditCatalogs result = adapter.catalogs();
    assertEquals(List.of("B3QX", "TAPP"), result.applications());
    assertEquals(List.of("HIGH", "NORMAL"), result.priorities());
  }

  @Test
  void healthUsesARealPingCommandAndPropagatesStorageFailures() {
    MongoDatabase database = mock(MongoDatabase.class);
    when(mongo.getDb()).thenReturn(database);
    adapter.checkConnection();
    verify(database).runCommand(new Document("ping", 1));
    MongoException failure = new MongoException("offline");
    when(collection.find(any(Bson.class))).thenThrow(failure);
    assertSame(failure, assertThrows(MongoException.class, () -> adapter.findByUid("u1")));
  }

  private void stubDistinct(String field, List<String> values) {
    DistinctIterable<String> iterable = mock(DistinctIterable.class, RETURNS_SELF);
    when(collection.distinct(eq(field), any(Bson.class), eq(String.class))).thenReturn(iterable);
    when(iterable.into(any(Collection.class))).thenAnswer(invocation -> {
      Collection<String> output = invocation.getArgument(0);
      output.addAll(values);
      return output;
    });
  }

  private List<Document> pipeline() {
    ArgumentCaptor<List> capture = ArgumentCaptor.forClass(List.class);
    verify(collection).aggregate(capture.capture());
    return capture.getValue();
  }

  private static Document facets() {
    return new Document("total", List.of(new Document("value", 7))).append("applications", List.of(new Document("value", 2)))
        .append("applicationCount", List.of(new Document("value", 2))).append("highPriority", List.of(new Document("value", 3)))
        .append("today", List.of(new Document("value", 4)));
  }

  private static Document bucket(String key, long count) { return new Document("_id", key).append("count", count); }
  private static BsonDocument bson(Bson value) { return value.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()); }
  private static AuditSearch search(String q, String app, String priority, Instant from, Instant to, String order) {
    return new AuditSearch(q, app, priority, from, to, 2, 10, "timestamp", order);
  }
}
