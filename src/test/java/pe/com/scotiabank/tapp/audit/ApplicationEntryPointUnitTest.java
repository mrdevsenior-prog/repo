package pe.com.scotiabank.tapp.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class ApplicationEntryPointUnitTest {
  @Test
  void startsSpringOnlyThroughMainAndPassesItsArguments() {
    String[] arguments = {"--server.port=0"};
    try (var spring = mockStatic(SpringApplication.class)) {
      assertDoesNotThrow(AuditApplication::new);
      spring.verifyNoInteractions();

      AuditApplication.main(arguments);
      spring.verify(() -> SpringApplication.run(AuditApplication.class, arguments));
    }
  }
}
