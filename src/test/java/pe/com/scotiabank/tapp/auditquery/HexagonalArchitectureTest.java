package pe.com.scotiabank.tapp.auditquery;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class HexagonalArchitectureTest {
  private static final String ROOT = "pe.com.scotiabank.tapp.auditquery";
  private static final JavaClasses PRODUCTION = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

  @Test
  void domainDependsOnlyOnJavaAndDomain() {
    classes().that().resideInAPackage(ROOT + ".domain..").should().onlyDependOnClassesThat()
        .resideInAnyPackage("java..", ROOT + ".domain..").check(PRODUCTION);
  }

  @Test
  void applicationRemainsIndependentOfFrameworksAndAdapters() {
    classes().that().resideInAPackage(ROOT + ".application..").should().onlyDependOnClassesThat()
        .resideInAnyPackage("java..", ROOT + ".domain..", ROOT + ".application..").check(PRODUCTION);
  }

  @Test
  void inboundAdaptersDoNotDependOnOutboundAdapters() {
    noClasses().that().resideInAPackage(ROOT + ".adapter.in..").should().dependOnClassesThat()
        .resideInAPackage(ROOT + ".adapter.out..").check(PRODUCTION);
  }

  @Test
  void outboundAdaptersDoNotDependOnInboundAdapters() {
    noClasses().that().resideInAPackage(ROOT + ".adapter.out..").should().dependOnClassesThat()
        .resideInAPackage(ROOT + ".adapter.in..").check(PRODUCTION);
  }

  @Test
  void portsAreInterfaces() {
    classes().that().resideInAnyPackage(ROOT + ".application.port.in..", ROOT + ".application.port.out..")
        .should().beInterfaces().check(PRODUCTION);
  }

  @Test
  void sharedAdapterTypesDoNotDependOnInboundOrOutboundAdapters() {
    noClasses().that().resideInAPackage(ROOT + ".adapter.common..").should().dependOnClassesThat()
        .resideInAnyPackage(ROOT + ".adapter.in..", ROOT + ".adapter.out..").check(PRODUCTION);
  }
}
