package pe.com.scotiabank.tapp.audit.bootstrap;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.com.scotiabank.tapp.audit.application.port.in.AuditQueryUseCase;
import pe.com.scotiabank.tapp.audit.application.port.out.AuditQueryPort;
import pe.com.scotiabank.tapp.audit.application.service.AuditQueryService;

@Configuration
public class QueryConfiguration {
  @Bean
  Clock auditClock(@Value("${app.timezone}") String timezone) {
    return Clock.system(ZoneId.of(timezone));
  }

  @Bean
  AuditQueryUseCase auditQueryService(AuditQueryPort port) {
    return new AuditQueryService(port);
  }
}
