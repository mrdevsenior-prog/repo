package pe.com.scotiabank.tapp.audit.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pe.com.scotiabank.tapp.audit.domain.model.AuditPayloadCopyUtils;

public record AuditLogRequest(
    @JsonProperty("log_timestamp") @NotNull OffsetDateTime logTimestamp,
    @JsonProperty("event_log_classification") @NotBlank String eventLogClassification,
    @JsonProperty("event_timestamp") @NotNull OffsetDateTime eventTimestamp,
    @JsonProperty("event_type") @NotBlank String eventType,
    @JsonProperty("event_priority") @NotBlank String eventPriority,
    @JsonProperty("event_category") @NotBlank String eventCategory,
    @JsonProperty("account_name") @NotBlank String accountName,
    @JsonProperty("source_host_name") @NotBlank String sourceHostName,
    @JsonProperty("source_ip_address") @NotBlank String sourceIpAddress,
    @JsonProperty("destination_host_name") @NotBlank String destinationHostName,
    @JsonProperty("destination_ip_address") @NotBlank String destinationIpAddress,
    @JsonProperty("session_identifier") @NotBlank String sessionIdentifier,
    @JsonProperty("audit_action") @NotBlank String auditAction,
    @JsonProperty("event_message") @NotNull @Valid EventMessage eventMessage) {

  public record EventMessage(
      @JsonProperty("app_code") @NotBlank String appCode,
      @JsonProperty("app_description") @NotBlank String appDescription,
      @JsonProperty("app_operation_code") @NotBlank String appOperationCode,
      @JsonProperty("app_operation_description") @NotBlank String appOperationDescription,
      @JsonProperty("type_id") @NotBlank String typeId,
      @JsonProperty("number_id") @NotBlank String numberId,
      @JsonProperty("device_id") @NotBlank String deviceId,
      @JsonProperty("operation_number") @NotBlank String operationNumber,
      @JsonProperty("cams_id") @NotBlank String camsId,
      @JsonProperty("bt_account") @NotBlank String btAccount,
      @JsonProperty("channel_payload_header") @NotNull Map<String, Object> channelPayloadHeader,
      @JsonProperty("channel_payload_detail") @NotNull
          List<@NotNull @Valid PayloadDetail> channelPayloadDetail) {

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

  public record PayloadDetail(@NotBlank String key, @NotBlank String value) {}
}
