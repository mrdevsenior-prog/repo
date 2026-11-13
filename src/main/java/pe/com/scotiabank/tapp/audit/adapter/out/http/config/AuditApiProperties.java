package pe.com.scotiabank.tapp.audit.adapter.out.http.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("audit.api")
public record AuditApiProperties(
    @NotNull URI url, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {

  @AssertTrue(message = "audit.api.url debe ser una URL absoluta HTTP o HTTPS")
  public boolean isHttpUrl() {
    return url != null
        && url.getHost() != null
        && ("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()));
  }

  @AssertTrue(message = "Los tiempos de espera deben ser mayores que cero")
  public boolean isTimeoutsPositive() {
    return connectTimeout != null
        && connectTimeout.isPositive()
        && readTimeout != null
        && readTimeout.isPositive();
  }
}
