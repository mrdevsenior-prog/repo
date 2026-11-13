package pe.com.scotiabank.tapp.audit.adapter.in.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.*;
import pe.com.scotiabank.tapp.audit.application.port.in.AuditQueryUseCase;
import pe.com.scotiabank.tapp.audit.domain.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditQueryController {
  private final AuditQueryUseCase service;

  public AuditQueryController(AuditQueryUseCase service) {
    this.service = service;
  }

  @GetMapping("/events")
  Mono<AuditPage> search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String application,
      @RequestParam(defaultValue = "") String priority,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "timestamp") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    return execute(
        () ->
            service.search(
                new AuditSearch(
                    q,
                    application,
                    priority,
                    parse(from),
                    parse(to),
                    page,
                    size,
                    sort,
                    direction)));
  }

  @GetMapping("/summary")
  Mono<AuditSummary> summary(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String application,
      @RequestParam(defaultValue = "") String priority,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return execute(
        () ->
            service.summarize(
                new AuditSearch(
                    q, application, priority, parse(from), parse(to), 0, 10, "timestamp", "desc")));
  }

  @GetMapping("/events/{uid}")
  Mono<Map<String, Object>> detail(@PathVariable String uid) {
    return execute(() -> service.detail(uid));
  }

  @GetMapping("/analytics")
  Mono<AuditAnalytics> analytics(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String application,
      @RequestParam(defaultValue = "") String priority,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return execute(
        () ->
            service.analytics(
                new AuditSearch(
                    q, application, priority, parse(from), parse(to), 0, 10, "timestamp", "desc")));
  }

  @GetMapping("/catalogs")
  Mono<AuditCatalogs> catalogs() {
    return execute(service::catalogs);
  }

  @GetMapping("/health")
  Mono<Map<String, String>> health() {
    return execute(
        () -> {
          service.checkConnection();
          return Map.of("status", "UP");
        });
  }

  private static <T> Mono<T> execute(Callable<T> query) {
    return Mono.fromCallable(query).subscribeOn(Schedulers.boundedElastic());
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
