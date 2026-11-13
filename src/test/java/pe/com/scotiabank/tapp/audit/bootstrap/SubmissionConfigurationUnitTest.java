package pe.com.scotiabank.tapp.audit.bootstrap;

import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.port.out.SendAuditPort;
import pe.com.scotiabank.tapp.audit.domain.model.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SubmissionConfigurationUnitTest {
  @Test
  void theConfiguredUseCaseDelegatesDeliveryToThePort() {
    SendAuditPort port = mock(SendAuditPort.class);
    AuditEvent event = mock(AuditEvent.class);
    AuditContext context = new AuditContext("v1", "WEB", "TAPP", "PE");
    AuditSubmission result = new AuditSubmission(new AuditResult(List.of(new AuditResult.AuditStatus("OK"))), 200);
    when(port.send(event, context)).thenReturn(Mono.just(result));
    StepVerifier.create(new ApplicationConfiguration().submitAuditUseCase(port).submit(event, context))
        .expectNext(result).verifyComplete();
  }
}
