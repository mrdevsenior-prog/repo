package pe.com.scotiabank.tapp.audit;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import pe.com.scotiabank.tapp.audit.LogAuditConsumerApplication;

class ApplicationEntryPointUnitTest {
  @Test
  void bothEntryPointsDelegateTheirArgumentsToSpringWithoutStartingServersInUnitTests() {
    String[] arguments = {"--server.port=0"};
    try (var spring = mockStatic(SpringApplication.class)) {
      new AuditApplication();
      new LogAuditConsumerApplication();
      AuditApplication.main(arguments);
      LogAuditConsumerApplication.main(arguments);
      spring.verify(() -> SpringApplication.run(AuditApplication.class, arguments));
      spring.verify(() -> SpringApplication.run(LogAuditConsumerApplication.class, arguments));
    }
  }
}
