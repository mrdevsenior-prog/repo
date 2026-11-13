package pe.com.scotiabank.tapp.audit.adapter.out.http.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

class DeliveryConfigurationUnitTest {
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"relative/path", "ftp://producer.invalid/log", "file:/tmp/log", "http:/missing-host"})
  void rejectsUrlsThatCannotBeUsedAsAnHttpDestination(String url) {
    assertFalse(properties(url == null ? null : URI.create(url), Duration.ofSeconds(1), Duration.ofSeconds(1)).isHttpUrl());
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://producer.invalid/log", "https://producer.invalid/log", "HTTPS://producer.invalid/log"})
  void acceptsAbsoluteHttpAndHttpsUrls(String url) {
    assertTrue(properties(URI.create(url), Duration.ofSeconds(1), Duration.ofSeconds(1)).isHttpUrl());
  }

  @Test
  void requiresBothTimeoutsToBePresentAndPositive() {
    Duration positive = Duration.ofSeconds(1);
    for (Duration invalid : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1)}) {
      assertFalse(properties(URI.create("http://producer.invalid"), invalid, positive).isTimeoutsPositive());
      assertFalse(properties(URI.create("http://producer.invalid"), positive, invalid).isTimeoutsPositive());
    }
    assertTrue(properties(URI.create("http://producer.invalid"), positive, positive).isTimeoutsPositive());
  }

  @Test
  void buildsTheConfiguredClientIncludingSubMillisecondConnectionTimeouts() {
    AuditClientConfiguration configuration = new AuditClientConfiguration();
    assertNotNull(configuration.auditWebClient(WebClient.builder(),
        properties(URI.create("http://producer.invalid"), Duration.ofNanos(1), Duration.ofSeconds(1))));
    assertNotNull(configuration.auditWebClient(WebClient.builder(),
        properties(URI.create("http://producer.invalid"), Duration.ofSeconds(3), Duration.ofSeconds(10))));
  }

  private static AuditApiProperties properties(URI url, Duration connect, Duration read) {
    return new AuditApiProperties(url, connect, read);
  }
}
