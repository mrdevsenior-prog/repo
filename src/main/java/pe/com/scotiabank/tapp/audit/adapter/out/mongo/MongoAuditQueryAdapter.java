package pe.com.scotiabank.tapp.audit.adapter.out.mongo;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.Projections;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import pe.com.scotiabank.tapp.audit.domain.*;
import pe.com.scotiabank.tapp.audit.application.port.out.AuditQueryPort;

@Repository
public class MongoAuditQueryAdapter implements AuditQueryPort {
  private static final Map<String, String> SORT_FIELDS = Map.of(
      "timestamp", "event_timestamp", "account", "account_name",
      "application", "event_message.app_code", "priority", "event_priority");
  private final MongoTemplate mongo;
  private final String collectionName;
  private final Clock clock;

  public MongoAuditQueryAdapter(MongoTemplate mongo, @Value("${app.mongodb.collection}") String collectionName,
                                Clock clock) {
    this.mongo = mongo;
    this.collectionName = collectionName;
    this.clock = clock;
  }

  @Override
  public AuditPage search(AuditSearch search) {
    Bson filter = filter(search);
    MongoCollection<Document> events = collection();
    long total = events.countDocuments(filter, new CountOptions().maxTime(5, TimeUnit.SECONDS));
    int order = "asc".equals(search.direction()) ? 1 : -1;
    Document sorting = new Document(SORT_FIELDS.get(search.sort()), order).append("_id", order);
    List<AuditEvent> items = events.find(filter)
        .projection(Projections.include("uid", "account_name", "event_message.app_code",
            "event_message.operation_number", "event_message.app_operation_description",
            "audit_action", "event_category", "event_priority", "event_type", "event_timestamp"))
        .sort(sorting).skip(search.page() * search.size()).limit(search.size())
        .maxTime(5, TimeUnit.SECONDS).map(this::toEvent).into(new ArrayList<>());
    return new AuditPage(items, total, search.page(), search.size());
  }

  @Override
  public AuditSummary summarize(AuditSearch search) {
    LocalDate today = LocalDate.now(clock);
    Date start = Date.from(today.atStartOfDay(clock.getZone()).toInstant());
    Date end = Date.from(today.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
    Document facets = new Document("total", List.of(new Document("$count", "value")))
        .append("applications", List.of(
            new Document("$group", new Document("_id", "$event_message.app_code")),
            new Document("$match", new Document("_id", new Document("$nin", Arrays.asList(null, "")))),
            new Document("$count", "value")))
        .append("highPriority", List.of(
            new Document("$match", new Document("event_priority", new Document("$in", List.of("HIGH", "CRITICAL")))),
            new Document("$count", "value")))
        .append("today", List.of(new Document("$match", new Document("event_timestamp",
            new Document("$gte", start).append("$lt", end))), new Document("$count", "value")));
    Document result = collection().aggregate(List.of(
        new Document("$match", filter(search)), new Document("$facet", facets)))
        .maxTime(5, TimeUnit.SECONDS).first();
    return new AuditSummary(countFacet(result, "total"), countFacet(result, "applications"),
        countFacet(result, "highPriority"), countFacet(result, "today"));
  }

  @Override
  public Optional<Map<String, Object>> findByUid(String uid) {
    Document event = collection().find(eq("uid", uid)).maxTime(5, TimeUnit.SECONDS).first();
    return Optional.ofNullable(event).map(this::jsonDocument);
  }

  @Override
  public AuditAnalytics analytics(AuditSearch search) {
    LocalDate today = LocalDate.now(clock);
    Date start = Date.from(today.atStartOfDay(clock.getZone()).toInstant());
    Date end = Date.from(today.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
    Document facets = new Document("total", List.of(new Document("$count", "value")))
        .append("applicationCount", List.of(
            new Document("$group", new Document("_id", "$event_message.app_code")),
            new Document("$match", new Document("_id", new Document("$nin", Arrays.asList(null, "")))),
            new Document("$count", "value")))
        .append("highPriority", List.of(new Document("$match", new Document("event_priority",
            new Document("$in", List.of("HIGH", "CRITICAL")))), new Document("$count", "value")))
        .append("today", List.of(new Document("$match", new Document("event_timestamp",
            new Document("$gte", start).append("$lt", end))), new Document("$count", "value")))
        .append("daily", temporalBuckets("%Y-%m-%d"))
        .append("hours", temporalBuckets("%H"))
        .append("applications", categoryBuckets("event_message.app_code"))
        .append("priorities", categoryBuckets("event_priority"))
        .append("actions", categoryBuckets("audit_action"))
        .append("undated", List.of(new Document("$match", new Document("event_timestamp",
            new Document("$not", new Document("$type", "date")))), new Document("$count", "value")));
    Document result = collection().aggregate(List.of(new Document("$match", filter(search)),
        new Document("$facet", facets))).maxTime(5, TimeUnit.SECONDS).first();
    List<AuditAnalytics.Bucket> daily = buckets(result, "daily");
    String rangeStart = search.from() != null ? search.from().atZone(clock.getZone()).toLocalDate().toString()
        : daily.isEmpty() ? today.minusDays(6).toString() : daily.getFirst().key();
    String rangeEnd = search.to() != null ? search.to().minusNanos(1).atZone(clock.getZone()).toLocalDate().toString()
        : daily.isEmpty() ? today.toString() : daily.getLast().key();
    return new AuditAnalytics(new AuditSummary(countFacet(result, "total"), countFacet(result, "applicationCount"),
        countFacet(result, "highPriority"), countFacet(result, "today")), daily,
        buckets(result, "applications"), buckets(result, "priorities"), buckets(result, "actions"),
        buckets(result, "hours"), countFacet(result, "undated"), rangeStart, rangeEnd,
        clock.getZone().getId(), clock.instant());
  }

  private List<Document> temporalBuckets(String format) {
    return List.of(new Document("$match", new Document("event_timestamp", new Document("$type", "date"))),
        new Document("$group", new Document("_id", new Document("$dateToString",
            new Document("format", format).append("date", "$event_timestamp")
                .append("timezone", clock.getZone().getId()))).append("count", new Document("$sum", 1))),
        new Document("$sort", new Document("_id", 1)));
  }

  private static List<Document> categoryBuckets(String field) {
    Document normalized = new Document("$ifNull", List.of("$" + field, ""));
    Document key = new Document("$cond", List.of(new Document("$eq", List.of(normalized, "")), "Sin dato", "$" + field));
    return List.of(new Document("$group", new Document("_id", key).append("count", new Document("$sum", 1))),
        new Document("$sort", new Document("count", -1).append("_id", 1)));
  }

  private static List<AuditAnalytics.Bucket> buckets(Document result, String name) {
    if (result == null) return List.of();
    List<Document> rows = result.getList(name, Document.class);
    return rows == null ? List.of() : rows.stream().map(row -> new AuditAnalytics.Bucket(
        Objects.toString(row.get("_id"), "Sin dato"), ((Number) row.get("count")).longValue())).toList();
  }

  @Override
  public AuditCatalogs catalogs() {
    return new AuditCatalogs(values("event_message.app_code"), values("event_priority"));
  }

  @Override
  public void checkConnection() { mongo.getDb().runCommand(new Document("ping", 1)); }

  private MongoCollection<Document> collection() { return mongo.getCollection(collectionName); }

  private List<String> values(String field) {
    return collection().distinct(field, new Document(field, new Document("$type", "string")), String.class)
        .maxTime(5, TimeUnit.SECONDS).into(new ArrayList<>()).stream()
        .filter(value -> !value.isBlank()).sorted().toList();
  }

  private Bson filter(AuditSearch search) {
    List<Bson> conditions = new ArrayList<>();
    if (!search.q().isEmpty()) {
      String literal = Pattern.quote(search.q());
      conditions.add(or(List.of("uid", "account_name", "session_identifier",
              "event_message.operation_number", "event_message.number_id", "event_message.bt_account")
          .stream().map(field -> regex(field, literal, "i")).toList()));
    }
    if (!search.application().isEmpty()) conditions.add(eq("event_message.app_code", search.application()));
    if (!search.priority().isEmpty()) conditions.add(eq("event_priority", search.priority()));
    if (search.from() != null) conditions.add(gte("event_timestamp", Date.from(search.from())));
    if (search.to() != null) conditions.add(lt("event_timestamp", Date.from(search.to())));
    return conditions.isEmpty() ? new Document() : and(conditions);
  }

  private AuditEvent toEvent(Document doc) {
    Document message = doc.get("event_message", Document.class);
    return new AuditEvent(text(doc, "uid"), text(doc, "account_name"), text(message, "app_code"),
        text(message, "operation_number"), text(doc, "audit_action"), text(doc, "event_category"),
        text(doc, "event_priority"), text(doc, "event_type"), instant(doc.get("event_timestamp")),
        text(message, "app_operation_description"));
  }

  private static String text(Document doc, String key) {
    Object value = doc == null ? null : doc.get(key);
    return value == null ? "" : value.toString();
  }

  private static Instant instant(Object value) {
    if (value instanceof Date date) return date.toInstant();
    if (value instanceof Instant timestamp) return timestamp;
    return null;
  }

  private static long countFacet(Document result, String key) {
    if (result == null) return 0;
    List<Document> values = result.getList(key, Document.class);
    return values == null || values.isEmpty() ? 0 : ((Number) values.getFirst().get("value")).longValue();
  }

  private Map<String, Object> jsonDocument(Map<String, ?> document) {
    Map<String, Object> output = new LinkedHashMap<>();
    document.forEach((key, value) -> output.put(key, jsonValue(value)));
    return output;
  }

  private Object jsonValue(Object value) {
    if (value instanceof ObjectId id) return id.toHexString();
    if (value instanceof Date date) return date.toInstant().toString();
    if (value instanceof Decimal128 decimal) return decimal.toString();
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> output = new LinkedHashMap<>();
      map.forEach((key, item) -> output.put(key.toString(), jsonValue(item)));
      return output;
    }
    if (value instanceof List<?> list) return list.stream().map(this::jsonValue).toList();
    if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
    return value;
  }
}
