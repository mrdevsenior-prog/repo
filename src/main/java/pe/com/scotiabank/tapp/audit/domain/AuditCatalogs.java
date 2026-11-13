package pe.com.scotiabank.tapp.audit.domain;

import java.util.List;

public record AuditCatalogs(List<String> applications, List<String> priorities) {
  public AuditCatalogs {
    applications = List.copyOf(applications);
    priorities = List.copyOf(priorities);
  }
}
