package pe.com.scotiabank.tapp.audit.adapter.out.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pe.com.scotiabank.tapp.audit.domain.model.AuditPayloadCopyUtils;

public record AuditApiRequest(
    @JsonProperty("log_timestamp") OffsetDateTime logTimestamp,
    @JsonProperty("event_log_classification") String eventLogClassification,
    @JsonProperty("event_timestamp") OffsetDateTime eventTimestamp,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("event_priority") String eventPriority,
    @JsonProperty("event_category") String eventCategory,
    @JsonProperty("account_name") String accountName,
    @JsonProperty("source_host_name") String sourceHostName,
    @JsonProperty("source_ip_address") String sourceIpAddress,
    @JsonProperty("destination_host_name") String destinationHostName,
    @JsonProperty("destination_ip_address") String destinationIpAddress,
    @JsonProperty("session_identifier") String sessionIdentifier,
    @JsonProperty("audit_action") String auditAction,
    @JsonProperty("event_message") EventMessage eventMessage) {

  public record EventMessage(
      @JsonProperty("app_code") String appCode,
      @JsonProperty("app_description") String appDescription,
      @JsonProperty("app_operation_code") String appOperationCode,
      @JsonProperty("app_operation_description") String appOperationDescription,
      @JsonProperty("type_id") String typeId,
      @JsonProperty("number_id") String numberId,
      @JsonProperty("device_id") String deviceId,
      @JsonProperty("operation_number") String operationNumber,
      @JsonProperty("cams_id") String camsId,
      @JsonProperty("bt_account") String btAccount,
      @JsonProperty("channel_payload_header") Map<String, Object> channelPayloadHeader,
      @JsonProperty("channel_payload_detail") List<PayloadDetail> channelPayloadDetail) {

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
