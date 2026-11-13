package pe.com.scotiabank.tapp.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LogAuditConsumerApplication {

  public static void main(String[] args) {
    SpringApplication.run(LogAuditConsumerApplication.class, args);
  }
}
