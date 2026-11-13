package pe.com.scotiabank.tapp.audit.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.port.in.SubmitAuditUseCase;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class SubmissionControllerUnitTest {
  private final AuditWebMapper mapper = Mappers.getMapper(AuditWebMapper.class);
  private final SubmitAuditUseCase useCase = mock(SubmitAuditUseCase.class);

  @Test
  void theSingleSubmissionAdapterPreservesTheEventContextAndProviderResponse() throws Exception {
    AuditLogRequest request;
    try (var stream = getClass().getResourceAsStream("/audit-request.json")) {
      request = JsonMapper.builder().build().readValue(stream, AuditLogRequest.class);
    }
    AuditContext context = new AuditContext("v1", "WEB", "TAPP", "PE");
    when(useCase.submit(mapper.toDomain(request), context))
        .thenReturn(
            Mono.just(
                new AuditSubmission(
                    new AuditResult(List.of(new AuditResult.AuditStatus("OK"))), 202)));
    var result =
        new AuditSubmissionController(useCase, mapper)
            .send("v1", "WEB", "TAPP", "PE", Mono.just(request));
    verifyNoInteractions(useCase);
    StepVerifier.create(result)
        .assertNext(
            response -> {
              assertEquals(202, response.getStatusCode().value());
              assertEquals("OK", response.getBody().data().getFirst().status());
            })
        .expectComplete()
        .verify(Duration.ofSeconds(2));
    verify(useCase).submit(mapper.toDomain(request), context);
  }
}
