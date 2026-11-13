package pe.com.scotiabank.tapp.audit.domain.model;

import java.util.ArrayList;
import java.util.List;

public record AuditResult(List<AuditStatus> data) {

  public AuditResult {
    data = data == null ? null : new ArrayList<>(data);
  }

  public List<AuditStatus> data() {
    return data == null ? null : new ArrayList<>(data);
  }

  public record AuditStatus(String status) {}
}
