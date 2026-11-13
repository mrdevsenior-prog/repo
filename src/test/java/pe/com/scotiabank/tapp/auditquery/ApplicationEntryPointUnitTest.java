package pe.com.scotiabank.tapp.auditquery;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import pe.com.scotiabank.tapp.audit.LogAuditConsumerApplication;

class ApplicationEntryPointUnitTest {
  @Test
  void bothEntryPointsDelegateTheirArgumentsToSpringWithoutStartingServersInUnitTests() {
    String[] arguments = {"--server.port=0"};
    try (var spring = mockStatic(SpringApplication.class)) {
      new AuditQueryApplication();
      new LogAuditConsumerApplication();
      AuditQueryApplication.main(arguments);
      LogAuditConsumerApplication.main(arguments);
      spring.verify(() -> SpringApplication.run(AuditQueryApplication.class, arguments));
      spring.verify(() -> SpringApplication.run(LogAuditConsumerApplication.class, arguments));
    }
  }
}
