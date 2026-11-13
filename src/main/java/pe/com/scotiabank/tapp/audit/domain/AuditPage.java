package pe.com.scotiabank.tapp.audit.domain;

import java.util.List;

public record AuditPage(List<AuditEvent> items, long total, int page, int size) {
  public AuditPage {
    items = List.copyOf(items);
  }
}
