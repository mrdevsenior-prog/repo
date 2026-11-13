package pe.com.scotiabank.tapp.auditquery.application.port.in;

import java.util.Map;
import pe.com.scotiabank.tapp.auditquery.domain.*;

public interface AuditQueryUseCase {
  AuditPage search(AuditSearch search);
  AuditSummary summarize(AuditSearch search);
  AuditAnalytics analytics(AuditSearch search);
  Map<String, Object> detail(String uid);
  AuditCatalogs catalogs();
  void checkConnection();
}
