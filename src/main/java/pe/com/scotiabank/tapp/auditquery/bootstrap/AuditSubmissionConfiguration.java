package pe.com.scotiabank.tapp.auditquery.bootstrap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.adapter.out.http.AuditApiClient;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditClientConfiguration;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.bootstrap.ApplicationConfiguration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditApiProperties.class)
@Import({ApplicationConfiguration.class, AuditClientConfiguration.class, AuditApiClient.class})
@ComponentScan(basePackageClasses = {AuditWebMapper.class, AuditApiMapper.class})
public class AuditSubmissionConfiguration implements WebMvcConfigurer {
  private final AuditApiProperties properties;

  public AuditSubmissionConfiguration(AuditApiProperties properties) { this.properties = properties; }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(properties.connectTimeout().plus(properties.readTimeout()).plusSeconds(1).toMillis());
  }
}
