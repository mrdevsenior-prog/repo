package pe.com.scotiabank.tapp.auditquery.demo;

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.UpdateOptions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.bson.Document;

public final class SeedDemo {
  private SeedDemo() {}

  public static void main(String[] args) {
    String uri = environment("MONGODB_URI", "mongodb://localhost:27017/?serverSelectionTimeoutMS=3000");
    String database = environment("MONGODB_DATABASE", "lineage_audit_demo");
    String collection = environment("MONGODB_COLLECTION", "tapp_audit_events");
    if (!database.endsWith("_demo")) throw new IllegalArgumentException("La carga requiere una base terminada en _demo.");
    try (var mongo = MongoClients.create(uri)) {
      var events = mongo.getDatabase(database).getCollection(collection);
      Instant now = Instant.now();
      String[] apps = {"TAPP", "B3QX", "TAPP", "TAPP"};
      String[] priorities = {"HIGH", "NORMAL", "LOW", "HIGH", "NORMAL", "NORMAL"};
      String[] descriptions = {"Crédito a cuenta beneficiaria", "Consulta de movimientos", "Validación de identidad", "Transferencia entre cuentas", "Inicio de sesión", "Consulta de saldo"};
      for (int i = 0; i < 36; i++) {
        String uid = "demo-audit-%03d".formatted(i + 1);
        Instant timestamp = now.minus(i * 2L, ChronoUnit.HOURS);
        Document message = new Document("app_code", apps[i % apps.length])
            .append("app_description", "Aplicación de demostración")
            .append("app_operation_code", "DEMO_%02d".formatted(i % descriptions.length + 1))
            .append("app_operation_description", descriptions[i % descriptions.length])
            .append("type_id", "DEMO").append("number_id", "DEMO-%04d".formatted(i + 1))
            .append("device_id", "device-demo-%02d".formatted(i % 5 + 1))
            .append("operation_number", "TXN-DEMO-%06d".formatted(i + 1))
            .append("cams_id", "CAMS-DEMO").append("bt_account", "DEMO-0000")
            .append("channel_payload_header", new Document("description", "Datos sintéticos de demostración")
                .append("channel_payload_detail", List.of(
                    new Document("key", "amount").append("value", "%d.00".formatted(50 + i * 10)),
                    new Document("key", "currency").append("value", "PEN"),
                    new Document("key", "transactionStatus").append("value", "SUCCESS"),
                    new Document("key", "synthetic").append("value", "true"))));
        Document event = new Document("uid", uid).append("account_name", "demo.usuario%02d".formatted(i % 7 + 1))
            .append("audit_action", i % 3 == 0 ? "EXECUTE" : "READ").append("audit_action_code", i % 3 == 0 ? "20" : "10")
            .append("event_category", "TERMINATED").append("event_category_code", "40")
            .append("event_priority", priorities[i % priorities.length])
            .append("event_type", "INFORMATION").append("event_type_code", "20")
            .append("event_log_classification", "AUDIT_LOG").append("event_message", message)
            .append("event_timestamp", Date.from(timestamp)).append("log_timestamp", Date.from(timestamp.plusMillis(35)))
            .append("expires_at", Date.from(timestamp.plus(365, ChronoUnit.DAYS)))
            .append("session_identifier", "session-demo-%04d".formatted(i + 1))
            .append("source_host_name", "demo-payment-service").append("source_ip_address", "192.0.2.10")
            .append("destination_host_name", "demo-audit-service").append("destination_ip_address", "192.0.2.20");
        events.updateOne(new Document("uid", uid), new Document("$setOnInsert", event), new UpdateOptions().upsert(true));
      }
      System.out.println("36 eventos sintéticos disponibles en " + database + "." + collection + ".");
    }
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
