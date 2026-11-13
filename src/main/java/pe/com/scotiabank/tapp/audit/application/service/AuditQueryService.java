package pe.com.scotiabank.tapp.audit.application.service;

import java.util.Map;
import java.util.NoSuchElementException;
import pe.com.scotiabank.tapp.audit.application.port.in.AuditQueryUseCase;
import pe.com.scotiabank.tapp.audit.application.port.out.AuditQueryPort;
import pe.com.scotiabank.tapp.audit.domain.*;

public final class AuditQueryService implements AuditQueryUseCase {
  private final AuditQueryPort queries;

  public AuditQueryService(AuditQueryPort queries) {
    this.queries = queries;
  }

  public AuditPage search(AuditSearch search) {
    return queries.search(search);
  }

  public AuditSummary summarize(AuditSearch search) {
    return queries.summarize(search);
  }

  public AuditAnalytics analytics(AuditSearch search) {
    return queries.analytics(search);
  }

  public AuditCatalogs catalogs() {
    return queries.catalogs();
  }

  public void checkConnection() {
    queries.checkConnection();
  }

  public Map<String, Object> detail(String uid) {
    if (uid == null || uid.isBlank() || uid.length() > 200) {
      throw new IllegalArgumentException("El identificador del evento no es válido.");
    }
    return queries
        .findByUid(uid)
        .orElseThrow(() -> new NoSuchElementException("No se encontró el evento solicitado."));
  }
}
