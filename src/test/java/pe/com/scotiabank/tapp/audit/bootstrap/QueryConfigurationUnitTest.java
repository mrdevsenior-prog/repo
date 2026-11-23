package pe.com.scotiabank.tapp.audit.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
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
}
