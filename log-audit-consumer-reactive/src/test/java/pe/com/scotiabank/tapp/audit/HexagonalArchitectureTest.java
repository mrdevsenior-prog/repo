package pe.com.scotiabank.tapp.audit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class HexagonalArchitectureTest {

  private static final String ROOT = "pe.com.scotiabank.tapp.audit";

  @Test
  void domainDependsOnlyOnJavaAndDomain() {
    assertProductionArchitecture(
        classes()
            .that()
            .resideInAPackage(ROOT + ".domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", ROOT + ".domain.."));
  }

  @Test
  void applicationDependsOnlyOnJavaDomainApplicationAndReactiveStreams() {
    assertProductionArchitecture(
        classes()
            .that()
            .resideInAPackage(ROOT + ".application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                ROOT + ".domain..",
                ROOT + ".application..",
                "reactor.core.publisher..",
                "org.reactivestreams.."));
  }

  @Test
  void inboundAdaptersDoNotDependOnOutboundAdapters() {
    assertProductionArchitecture(
        noClasses()
            .that()
            .resideInAPackage(ROOT + ".adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + ".adapter.out.."));
  }

  @Test
  void outboundAdaptersDoNotDependOnInboundAdapters() {
    assertProductionArchitecture(
        noClasses()
            .that()
            .resideInAPackage(ROOT + ".adapter.out..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage(ROOT + ".adapter.in.."));
  }

  @Test
  void inputAndOutputPortsAreInterfaces() {
    assertProductionArchitecture(
        classes()
            .that()
            .resideInAnyPackage(ROOT + ".application.port.in..", ROOT + ".application.port.out..")
            .should()
            .beInterfaces());
  }

  @Test
  void productionDoesNotDependOnBlockingHttpClientsOrServletStack() {
    assertProductionArchitecture(
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.web.client..",
                "org.springframework.web.servlet..",
                "jakarta.servlet..",
                "java.net.http.."));
  }

  @Test
  void productionDoesNotBlockReactivePublishersIndefinitely() {
    assertProductionArchitecture(noClasses().should().callMethod(Mono.class, "block"));
  }

  @Test
  void productionDoesNotBlockReactivePublishersWithTimeout() {
    assertProductionArchitecture(
        noClasses().should().callMethod(Mono.class, "block", Duration.class));
  }

  private static void assertProductionArchitecture(ArchRule rule) {
    rule.check(
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT));
  }
}
