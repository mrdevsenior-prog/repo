package pe.com.scotiabank.tapp.audit.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaderConstants;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogResponse;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.application.port.in.SubmitAuditUseCase;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/audit/security/log")
public class AuditLogController {

  private final SubmitAuditUseCase useCase;
  private final AuditWebMapper mapper;

  public AuditLogController(SubmitAuditUseCase useCase, AuditWebMapper mapper) {
    this.useCase = useCase;
    this.mapper = mapper;
  }

  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<AuditLogResponse>> send(
      @RequestHeader(AuditHttpHeaderConstants.API_VERSION) @NotBlank String apiVersion,
      @RequestHeader(AuditHttpHeaderConstants.CHANNEL_ID) @NotBlank String channelId,
      @RequestHeader(AuditHttpHeaderConstants.ORIGINATING_APPL_CODE) @NotBlank
          String originatingApplCode,
      @RequestHeader(AuditHttpHeaderConstants.COUNTRY_CODE) @NotBlank String countryCode,
      @Valid @RequestBody Mono<AuditLogRequest> request) {
    return request
        .flatMap(
            body ->
                useCase.submit(
                    mapper.toDomain(body),
                    mapper.toContext(apiVersion, channelId, originatingApplCode, countryCode)))
        .map(
            submission ->
                ResponseEntity.status(submission.providerStatusCode())
                    .body(mapper.toResponse(submission.result())));
  }
}
