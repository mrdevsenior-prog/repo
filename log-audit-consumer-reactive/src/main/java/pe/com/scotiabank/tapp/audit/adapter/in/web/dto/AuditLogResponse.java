package pe.com.scotiabank.tapp.audit.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public record AuditLogResponse(@NotEmpty List<@NotNull @Valid AuditStatus> data) {

  public AuditLogResponse {
    data = data == null ? null : new ArrayList<>(data);
  }

  public List<AuditStatus> data() {
    return data == null ? null : new ArrayList<>(data);
  }

  public record AuditStatus(@NotBlank String status) {}
}
