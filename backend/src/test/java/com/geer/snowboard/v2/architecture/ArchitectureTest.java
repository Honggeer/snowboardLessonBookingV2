package com.geer.snowboard.v2.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geer.snowboard.v2.architecture.fixture.domain.BadDomainDependency;
import com.geer.snowboard.v2.bookings.fixture.BadBookingDependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private final ClassFileImporter productionImporter = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void realClassesRespectHexagonalAndModuleBoundaries() {
        JavaClasses classes = productionImporter.importPackages("com.geer.snowboard.v2");
        long domainClasses = classes.stream()
                .filter(type -> type.getPackageName().contains(".domain"))
                .count();
        long adapterClasses = classes.stream()
                .filter(type -> type.getPackageName().contains(".adapter"))
                .count();
        assertTrue(domainClasses > 0, "a real domain class must be checked");
        assertTrue(adapterClasses > 0, "a real adapter class must be checked");

        ArchitectureRules.all().forEach(rule -> rule.check(classes));
    }

    @Test
    void forbiddenDomainFrameworkDependencyIsDetected() {
        JavaClasses counterexample = new ClassFileImporter()
                .importClasses(BadDomainDependency.class);

        assertTrue(ArchitectureRules.domainAndPortsArePure()
                .evaluate(counterexample).hasViolation());
    }

    @Test
    void crossModuleAccessToDomainIsDetected() {
        JavaClasses counterexample = new ClassFileImporter()
                .importClasses(BadBookingDependency.class);

        assertTrue(ArchitectureRules.onlyPublishedModuleApis()
                .evaluate(counterexample).hasViolation());
    }
}
