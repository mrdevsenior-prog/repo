package pe.com.scotiabank.tapp.auditquery.domain;

import java.time.Instant;
import java.util.Set;

public record AuditSearch(String q, String application, String priority, Instant from, Instant to,
                          int page, int size, String sort, String direction) {
  public AuditSearch {
    q = normalize(q);
    application = normalize(application);
    priority = normalize(priority);
    if (q.length() > 120 || application.length() > 40 || priority.length() > 40) {
      throw new IllegalArgumentException("El texto de búsqueda es demasiado largo.");
    }
    if (page < 0 || page > 10_000 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Página inválida. El tamaño debe estar entre 1 y 100.");
    }
    if (from != null && to != null && !from.isBefore(to)) {
      throw new IllegalArgumentException("La fecha desde debe ser anterior a la fecha hasta.");
    }
    if (!Set.of("timestamp", "account", "application", "priority").contains(sort)) {
      throw new IllegalArgumentException("El campo de ordenamiento no está permitido.");
    }
    if (!Set.of("asc", "desc").contains(direction)) {
      throw new IllegalArgumentException("El orden debe ser asc o desc.");
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip();
  }
}
