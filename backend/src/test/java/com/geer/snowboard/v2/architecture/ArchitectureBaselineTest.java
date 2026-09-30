package com.geer.snowboard.v2.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geer.snowboard.v2.architecture.ArchitectureBaseline.Category;
import com.geer.snowboard.v2.architecture.ArchitectureBaseline.ExceptionEntry;
import com.geer.snowboard.v2.architecture.ArchitectureBaseline.Violation;
import com.geer.snowboard.v2.architecture.fixture.domain.BadDomainDependency;
import com.geer.snowboard.v2.bookings.fixture.BadBookingDependency;
import com.geer.snowboard.v2.bookings.fixture.BookingsCycle;
import com.geer.snowboard.v2.bookings.fixture.ExtraBookingsCycleEdge;
import com.geer.snowboard.v2.bookings.adapter.in.fixture.BadCrossInboundDependency;
import com.geer.snowboard.v2.identity.adapter.in.fixture.BadInboundDependency;
import com.geer.snowboard.v2.identity.application.port.in.BadPortDependency;
import com.geer.snowboard.v2.identity.application.service.BadApplicationDependency;
import com.geer.snowboard.v2.identity.fixture.IdentityCycle;
import com.geer.snowboard.v2.scheduling.fixture.SchedulingCycle;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchitectureBaselineTest {
    private static final Violation OLD = new Violation(Category.INBOUND_BOUNDARY, "INBOUND_BOUNDARY:source.A->target.B", Set.of("DEP-03"));
    private static final Violation REPLACEMENT = new Violation(Category.INBOUND_BOUNDARY, "INBOUND_BOUNDARY:source.A->target.C", Set.of("DEP-03"));
    private static final ExceptionEntry ACCEPTED = new ExceptionEntry(OLD.id(),
            "Legacy scheduler wiring cannot change before its separate migration is approved",
            "TODO-0011", "Remove when the scheduler migration is complete");

    @Test
    void unregisteredViolationFailsWithCategoryAndIdentity() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> ArchitectureBaseline.verify(Set.of(OLD), counts(0), Set.of()));
        assertTrue(error.getMessage().contains("INBOUND_BOUNDARY"));
        assertTrue(error.getMessage().contains(OLD.id()));
    }

    @Test
    void sameCountReplacementStillFails() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> ArchitectureBaseline.verify(Set.of(REPLACEMENT), counts(1), Set.of(ACCEPTED)));
        assertTrue(error.getMessage().contains(REPLACEMENT.id()));
        assertTrue(error.getMessage().contains(OLD.id()));
    }

    @Test
    void changingOnlyTheNumberFails() {
        assertThrows(AssertionError.class, () -> ArchitectureBaseline.verify(Set.of(OLD), counts(1), Set.of()));
    }

    @Test
    void incompleteExceptionMetadataFails() {
        for (ExceptionEntry incomplete : Set.of(
                new ExceptionEntry(OLD.id(), " ", "TODO-0011", "Remove after migration"),
                new ExceptionEntry(OLD.id(), "TODO", "TODO-0011", "Remove after migration"),
                new ExceptionEntry(OLD.id(), "TODO: document later", "TODO-0011", "Remove after migration"),
                new ExceptionEntry(OLD.id(), "Concrete reason", " ", "Remove after migration"),
                new ExceptionEntry(OLD.id(), "Concrete reason", "TODO-0011", " "))) {
            assertThrows(AssertionError.class,
                    () -> ArchitectureBaseline.verify(Set.of(OLD), counts(1), Set.of(incomplete)));
        }
    }

    @Test
    void staleExceptionMustBeRemovedAndCountReduced() {
        assertThrows(AssertionError.class,
                () -> ArchitectureBaseline.verify(Set.of(), counts(1), Set.of(ACCEPTED)));
        assertThrows(AssertionError.class,
                () -> ArchitectureBaseline.verify(Set.of(), counts(1), Set.of()));
        ArchitectureBaseline.verify(Set.of(), counts(0), Set.of());
    }

    @Test
    void exactDocumentedExceptionPasses() {
        ArchitectureBaseline.verify(Set.of(OLD), counts(1), Set.of(ACCEPTED));
        assertEquals(6, Category.values().length);
    }

    @Test
    void scannerClassifiesEveryBoundaryAndUsesStableEdgeIdentities() {
        var classes = new ClassFileImporter().importClasses(BadDomainDependency.class,
                BadPortDependency.class, BadApplicationDependency.class, BadInboundDependency.class,
                BadBookingDependency.class, IdentityCycle.class, BookingsCycle.class);
        Set<Violation> violations = ArchitectureBaseline.scan(classes);
        for (Category category : Category.values()) {
            assertTrue(violations.stream().anyMatch(v -> v.category() == category),
                    "missing fixture for " + category);
        }
        assertTrue(violations.stream().anyMatch(v -> v.category() == Category.CROSS_MODULE_INTERNAL
                && v.ruleIds().contains("DEP-04")));
        assertTrue(violations.stream().filter(v -> v.category() == Category.INBOUND_BOUNDARY)
                .allMatch(v -> !v.id().contains(".java:")), "identity must not contain line numbers");
        assertEquals(1, violations.stream().filter(v -> v.id().contains("BadInboundDependency->")
                && v.id().contains("IdentityService")).count(),
                "multiple members using the same class edge count once");
    }

    @Test
    void newDirectedEdgeInsideSameModuleCycleGetsNewIdentity() {
        var importer = new ClassFileImporter();
        Set<Violation> original = ArchitectureBaseline.scan(importer.importClasses(
                IdentityCycle.class, BookingsCycle.class, SchedulingCycle.class));
        Set<Violation> changed = ArchitectureBaseline.scan(importer.importClasses(
                IdentityCycle.class, BookingsCycle.class, SchedulingCycle.class, ExtraBookingsCycleEdge.class));
        Set<String> oldCycleIds = original.stream().filter(v -> v.category() == Category.MODULE_CYCLE)
                .map(Violation::id).collect(java.util.stream.Collectors.toSet());
        Set<String> newCycleIds = changed.stream().filter(v -> v.category() == Category.MODULE_CYCLE)
                .map(Violation::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(1, oldCycleIds.size());
        assertEquals(1, newCycleIds.size());
        assertTrue(!oldCycleIds.equals(newCycleIds));
        assertTrue(newCycleIds.iterator().next().contains("bookings->scheduling"));
    }

    @Test
    void overlappingRuleBreaksHaveOnePrimaryCategoryAndAllRuleIds() {
        Set<Violation> violations = ArchitectureBaseline.scan(
                new ClassFileImporter().importClasses(BadCrossInboundDependency.class));
        Violation edge = violations.stream().filter(v -> v.id().contains("BadCrossInboundDependency->"))
                .findFirst().orElseThrow();
        assertEquals(Category.CROSS_MODULE_INTERNAL, edge.category());
        assertTrue(edge.ruleIds().containsAll(Set.of("DEP-04", "DEP-03", "ARCH-04", "ARCH-05")));
        assertEquals(1, violations.stream().filter(v -> v.id().contains("BadCrossInboundDependency->")).count());
    }

    private static Map<Category, Integer> counts(int inbound) {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Category category : Category.values()) counts.put(category, 0);
        counts.put(Category.INBOUND_BOUNDARY, inbound);
        return counts;
    }
}
