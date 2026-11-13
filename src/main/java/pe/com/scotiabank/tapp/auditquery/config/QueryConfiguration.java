package pe.com.scotiabank.tapp.auditquery.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.com.scotiabank.tapp.auditquery.application.AuditQueryService;
import pe.com.scotiabank.tapp.auditquery.domain.AuditQueryPort;

@Configuration
public class QueryConfiguration {
  @Bean
  Clock auditClock(@Value("${app.timezone}") String timezone) {
    return Clock.system(ZoneId.of(timezone));
  }

  @Bean
  AuditQueryService auditQueryService(AuditQueryPort port) { return new AuditQueryService(port); }
}
