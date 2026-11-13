package pe.com.scotiabank.tapp.audit.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.application.port.out.AuditQueryPort;

class QueryConfigurationUnitTest {
  @Test
  void createsTheConfiguredTimezoneAndWiresTheQueryPort() {
    QueryConfiguration configuration = new QueryConfiguration();
    assertEquals(ZoneId.of("America/Lima"), configuration.auditClock("America/Lima").getZone());
    assertThrows(DateTimeException.class, () -> configuration.auditClock("invalid/timezone"));
    AuditQueryPort port = mock(AuditQueryPort.class);
    configuration.auditQueryService(port).checkConnection();
    verify(port).checkConnection();
  }

  @Test
  void asyncRequestTimeoutIncludesTheCompleteDeliveryDeadlineAndAResponseMargin() {
    AuditApiProperties properties = new AuditApiProperties(URI.create("http://producer.invalid"),
        Duration.ofSeconds(3), Duration.ofSeconds(10));
    AsyncSupportConfigurer configurer = mock(AsyncSupportConfigurer.class);
    new AuditSubmissionConfiguration(properties).configureAsyncSupport(configurer);
    verify(configurer).setDefaultTimeout(14000);
  }
}
