package com.geer.snowboard.v2.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;

final class ArchitectureRules {

    private static final String ROOT = "com.geer.snowboard.v2.";
    private static final Set<String> BUSINESS_MODULES = Set.of(
            "identity", "students", "catalog", "scheduling", "bookings",
            "notifications", "media");

    private ArchitectureRules() {}

    static List<ArchRule> all() {
        return List.of(
                domainAndPortsArePure(),
                noClasses().that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAPackage("..adapter.."),
                noClasses().that().resideInAPackage("..adapter.in..")
                        .should().dependOnClassesThat().resideInAPackage("..adapter.out.."),
                onlyPublishedModuleApis(),
                slices().matching("com.geer.snowboard.v2.(*)..").should().beFreeOfCycles());
    }

    static ArchRule domainAndPortsArePure() {
        return noClasses().that().resideInAnyPackage("..domain..", "..application.port..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "javax..", "java.sql..",
                        "org.apache.ibatis..", "software.amazon.awssdk..", "..adapter..");
    }

    static ArchRule onlyPublishedModuleApis() {
        return classes().should(new ArchCondition<>(
                "depend on other business modules only through published inbound ports") {
            @Override
            public void check(JavaClass source, ConditionEvents events) {
                String sourceModule = moduleOf(source.getName());
                if (sourceModule == null) {
                    return;
                }
                for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                    String targetName = dependency.getTargetClass().getName();
                    String targetModule = moduleOf(targetName);
                    if (targetModule != null && !sourceModule.equals(targetModule)
                            && !targetName.startsWith(ROOT + targetModule + ".application.port.in.")) {
                        events.add(SimpleConditionEvent.violated(source,
                                source.getName() + " depends on internal class " + targetName));
                    }
                }
            }
        });
    }

    private static String moduleOf(String className) {
        if (!className.startsWith(ROOT)) {
            return null;
        }
        String remainder = className.substring(ROOT.length());
        int separator = remainder.indexOf('.');
        String candidate = separator < 0 ? remainder : remainder.substring(0, separator);
        return BUSINESS_MODULES.contains(candidate) ? candidate : null;
    }
}
