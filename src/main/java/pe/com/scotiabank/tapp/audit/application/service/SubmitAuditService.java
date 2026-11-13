package pe.com.scotiabank.tapp.audit.application.service;

import java.util.Objects;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.port.in.SubmitAuditUseCase;
import pe.com.scotiabank.tapp.audit.application.port.out.SendAuditPort;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import reactor.core.publisher.Mono;

public final class SubmitAuditService implements SubmitAuditUseCase {

  private final SendAuditPort sendAuditPort;

  public SubmitAuditService(SendAuditPort sendAuditPort) {
    this.sendAuditPort = Objects.requireNonNull(sendAuditPort);
  }

  @Override
  public Mono<AuditSubmission> submit(AuditEvent event, AuditContext context) {
    return Mono.defer(
        () ->
            sendAuditPort.send(
                Objects.requireNonNull(event, "El evento es requerido"),
                Objects.requireNonNull(context, "El contexto es requerido")));
  }
}
