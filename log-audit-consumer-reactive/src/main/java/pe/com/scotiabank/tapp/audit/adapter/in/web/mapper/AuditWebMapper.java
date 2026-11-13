package pe.com.scotiabank.tapp.audit.adapter.in.web.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import pe.com.scotiabank.tapp.audit.adapter.common.mapper.StrictMapperConfig;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogResponse;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;

@Mapper(config = StrictMapperConfig.class)
public interface AuditWebMapper {

  AuditEvent toDomain(AuditLogRequest request);

  AuditEvent.EventMessage toDomain(AuditLogRequest.EventMessage message);

  AuditEvent.PayloadDetail toDomain(AuditLogRequest.PayloadDetail detail);

  @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
  @Mapping(target = "apiVersion", source = "apiVersion")
  @Mapping(target = "channelId", source = "channelId")
  @Mapping(target = "originatingApplCode", source = "originatingApplCode")
  @Mapping(target = "countryCode", source = "countryCode")
  AuditContext toContext(
      String apiVersion, String channelId, String originatingApplCode, String countryCode);

  AuditLogResponse toResponse(AuditResult result);

  AuditLogResponse.AuditStatus toResponse(AuditResult.AuditStatus status);
}
