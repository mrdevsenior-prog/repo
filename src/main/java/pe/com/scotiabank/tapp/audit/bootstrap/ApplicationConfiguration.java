package pe.com.scotiabank.tapp.audit.bootstrap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.com.scotiabank.tapp.audit.application.port.in.SubmitAuditUseCase;
import pe.com.scotiabank.tapp.audit.application.port.out.SendAuditPort;
import pe.com.scotiabank.tapp.audit.application.service.SubmitAuditService;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfiguration {

  @Bean
  SubmitAuditUseCase submitAuditUseCase(SendAuditPort sendAuditPort) {
    return new SubmitAuditService(sendAuditPort);
  }
}
