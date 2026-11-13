package pe.com.scotiabank.tapp.audit.adapter.out.http.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public record AuditApiResponse(@NotEmpty List<@NotNull @Valid AuditStatus> data) {

  public AuditApiResponse {
    data = data == null ? null : new ArrayList<>(data);
  }

  public List<AuditStatus> data() {
    return data == null ? null : new ArrayList<>(data);
  }

  public record AuditStatus(@NotBlank String status) {}
}
