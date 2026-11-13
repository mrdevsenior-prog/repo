package pe.com.scotiabank.tapp.audit.application.port.out;

import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface SendAuditPort {

  Mono<AuditSubmission> send(AuditEvent event, AuditContext context);
}
