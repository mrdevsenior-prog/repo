package pe.com.scotiabank.tapp.audit.adapter.out.http.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
public class AuditClientConfiguration {

  private static final int MAX_RESPONSE_BYTES = 256 * 1024;

  @Bean
  WebClient auditWebClient(WebClient.Builder builder, AuditApiProperties properties) {
    int connectTimeoutMillis = Math.toIntExact(Math.max(1, properties.connectTimeout().toMillis()));
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(properties.readTimeout())
            .followRedirect(false)
            .disableRetry(true);
    return builder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
        .build();
  }
}
