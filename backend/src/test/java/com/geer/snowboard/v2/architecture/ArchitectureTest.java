package com.geer.snowboard.v2.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geer.snowboard.v2.architecture.fixture.domain.BadDomainDependency;
import com.geer.snowboard.v2.bookings.fixture.BadBookingDependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    // Every accepted exception must have its exact edge identity, concrete reason,
    // approved plan/ADR or ticket, and removal condition in this registry.
    // Increase exactly one category count per new accepted edge; reduce it on removal.
    // The approved 0003 baseline has no exceptions.
    private static final Map<ArchitectureBaseline.Category, Integer> BASELINE = Map.of(
            ArchitectureBaseline.Category.DOMAIN_PURITY, 0,
            ArchitectureBaseline.Category.PORT_PURITY, 0,
            ArchitectureBaseline.Category.APPLICATION_BOUNDARY, 0,
            ArchitectureBaseline.Category.INBOUND_BOUNDARY, 0,
            ArchitectureBaseline.Category.CROSS_MODULE_INTERNAL, 0,
            ArchitectureBaseline.Category.MODULE_CYCLE, 0);
    private static final Set<ArchitectureBaseline.ExceptionEntry> EXCEPTIONS = Set.of();

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

        ArchitectureBaseline.verify(ArchitectureBaseline.scan(classes), BASELINE, EXCEPTIONS);
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

    @Test
    void inboundAdaptersUsePublishedApplicationContracts() {
        JavaClasses classes = productionImporter.importPackages("com.geer.snowboard.v2");

        ArchRuleDefinition.noClasses().that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..application.service..")
                .check(classes);
    }
}
