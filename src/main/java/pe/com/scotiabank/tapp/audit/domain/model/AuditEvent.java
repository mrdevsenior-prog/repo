package pe.com.scotiabank.tapp.audit.domain.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record AuditEvent(
    OffsetDateTime logTimestamp,
    String eventLogClassification,
    OffsetDateTime eventTimestamp,
    String eventType,
    String eventPriority,
    String eventCategory,
    String accountName,
    String sourceHostName,
    String sourceIpAddress,
    String destinationHostName,
    String destinationIpAddress,
    String sessionIdentifier,
    String auditAction,
    EventMessage eventMessage) {

  public record EventMessage(
      String appCode,
      String appDescription,
      String appOperationCode,
      String appOperationDescription,
      String typeId,
      String numberId,
      String deviceId,
      String operationNumber,
      String camsId,
      String btAccount,
      Map<String, Object> channelPayloadHeader,
      List<PayloadDetail> channelPayloadDetail) {

    public EventMessage {
      channelPayloadHeader = AuditPayloadCopyUtils.copyHeader(channelPayloadHeader);
      channelPayloadDetail =
          channelPayloadDetail == null ? null : new ArrayList<>(channelPayloadDetail);
    }

    public Map<String, Object> channelPayloadHeader() {
      return AuditPayloadCopyUtils.copyHeader(channelPayloadHeader);
    }

    public List<PayloadDetail> channelPayloadDetail() {
      return channelPayloadDetail == null ? null : new ArrayList<>(channelPayloadDetail);
    }
  }

  public record PayloadDetail(String key, String value) {}
}
