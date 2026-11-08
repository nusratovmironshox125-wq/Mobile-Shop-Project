package sayt;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

class ArchitectureTests {
    private static final com.tngtech.archunit.core.domain.JavaClasses MAIN_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("sayt");

    @Test
    void controllersUseServicesAndExposeDtos() {
        classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("sayt.controller..").check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("sayt.controller..").should().dependOnClassesThat()
            .resideInAnyPackage("sayt.repository..", "sayt.entity..", "sayt.integration..",
                "org.springframework.jdbc..", "jakarta.persistence..", "javax.sql..").check(MAIN_CLASSES);
    }

    @Test
    void entitiesAndRepositoriesStayIndependentOfHttpAndServices() {
        classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("sayt.entity..").check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("sayt.entity..").should().dependOnClassesThat()
            .resideInAnyPackage("sayt.controller..", "sayt.dto..", "sayt.service..",
                "org.springframework.web..", "jakarta.servlet..").check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("sayt.repository..").should().dependOnClassesThat()
            .resideInAnyPackage("sayt.controller..", "sayt.service..", "sayt.integration..").check(MAIN_CLASSES);
    }

    @Test
    void servicesUseRepositoryAndIntegrationContracts() {
        classes().that().areAnnotatedWith(Service.class).should().resideInAPackage("sayt.service..").check(MAIN_CLASSES);
        noClasses().that().resideInAPackage("sayt.service..").should().dependOnClassesThat()
            .resideInAnyPackage("sayt.integration..", "org.springframework.jdbc..", "java.net.http..",
                "javax.sql..", "jakarta.persistence..").check(MAIN_CLASSES);
        noFields().should().beAnnotatedWith(Autowired.class).check(MAIN_CLASSES);
    }
}
