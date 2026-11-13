package pe.com.scotiabank.tapp.auditquery.adapter.in.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import pe.com.scotiabank.tapp.auditquery.application.port.in.AuditQueryUseCase;
import pe.com.scotiabank.tapp.auditquery.domain.*;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {
  private final AuditQueryUseCase service;

  public AuditQueryController(AuditQueryUseCase service) { this.service = service; }

  @GetMapping("/events")
  AuditPage search(@RequestParam(defaultValue = "") String q,
                   @RequestParam(defaultValue = "") String application,
                   @RequestParam(defaultValue = "") String priority,
                   @RequestParam(required = false) String from,
                   @RequestParam(required = false) String to,
                   @RequestParam(defaultValue = "0") int page,
                   @RequestParam(defaultValue = "10") int size,
                   @RequestParam(defaultValue = "timestamp") String sort,
                   @RequestParam(defaultValue = "desc") String direction) {
    return service.search(new AuditSearch(q, application, priority, parse(from), parse(to), page, size, sort, direction));
  }

  @GetMapping("/summary")
  AuditSummary summary(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "") String application,
                       @RequestParam(defaultValue = "") String priority,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to) {
    return service.summarize(new AuditSearch(q, application, priority, parse(from), parse(to), 0, 10, "timestamp", "desc"));
  }

  @GetMapping("/events/{uid}")
  Map<String, Object> detail(@PathVariable String uid) { return service.detail(uid); }

  @GetMapping("/analytics")
  AuditAnalytics analytics(@RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "") String application,
                           @RequestParam(defaultValue = "") String priority,
                           @RequestParam(required = false) String from,
                           @RequestParam(required = false) String to) {
    return service.analytics(new AuditSearch(q, application, priority, parse(from), parse(to),
        0, 10, "timestamp", "desc"));
  }

  @GetMapping("/catalogs")
  AuditCatalogs catalogs() { return service.catalogs(); }

  @GetMapping("/health")
  Map<String, String> health() {
    service.checkConnection();
    return Map.of("status", "UP");
  }

  private static Instant parse(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Las fechas deben tener formato ISO 8601 y zona horaria.");
    }
  }
}
