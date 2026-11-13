package pe.com.scotiabank.tapp.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import pe.com.scotiabank.tapp.audit.adapter.in.web.AuditLogController;
import pe.com.scotiabank.tapp.audit.adapter.in.web.error.ApiExceptionHandler;

@SpringBootApplication
@ComponentScan(basePackageClasses = AuditApplication.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        LogAuditConsumerApplication.class, AuditLogController.class, ApiExceptionHandler.class})
})
public class AuditApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuditApplication.class, args);
  }
}
