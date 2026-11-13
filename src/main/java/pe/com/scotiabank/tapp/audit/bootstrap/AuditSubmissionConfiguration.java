package pe.com.scotiabank.tapp.audit.bootstrap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditApiProperties.class)
public class AuditSubmissionConfiguration implements WebMvcConfigurer {
  private final AuditApiProperties properties;

  public AuditSubmissionConfiguration(AuditApiProperties properties) { this.properties = properties; }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(properties.connectTimeout().plus(properties.readTimeout()).plusSeconds(1).toMillis());
  }
}
