package pe.com.scotiabank.tapp.auditquery.domain;

import java.util.Map;
import java.util.Optional;

public interface AuditQueryPort {
  AuditPage search(AuditSearch search);
  AuditSummary summarize(AuditSearch search);
  AuditAnalytics analytics(AuditSearch search);
  Optional<Map<String, Object>> findByUid(String uid);
  AuditCatalogs catalogs();
  void checkConnection();
}
