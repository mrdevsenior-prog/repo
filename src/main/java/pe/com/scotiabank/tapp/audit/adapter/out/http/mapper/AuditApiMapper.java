package pe.com.scotiabank.tapp.audit.adapter.out.http.mapper;

import org.mapstruct.Mapper;
import pe.com.scotiabank.tapp.audit.adapter.common.mapper.StrictMapperConfig;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiRequest;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiResponse;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;

@Mapper(config = StrictMapperConfig.class)
public interface AuditApiMapper {

  AuditApiRequest toRequest(AuditEvent event);

  AuditApiRequest.EventMessage toRequest(AuditEvent.EventMessage message);

  AuditApiRequest.PayloadDetail toRequest(AuditEvent.PayloadDetail detail);

  AuditResult toDomain(AuditApiResponse response);

  AuditResult.AuditStatus toDomain(AuditApiResponse.AuditStatus status);
}
