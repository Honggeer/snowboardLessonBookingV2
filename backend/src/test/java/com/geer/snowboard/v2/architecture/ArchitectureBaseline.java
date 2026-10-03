package com.geer.snowboard.v2.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Stable, reviewable identity for architecture violations in production classes. */
final class ArchitectureBaseline {
    private static final String ROOT = "com.geer.snowboard.v2.";
    private static final Set<String> BUSINESS_MODULES = Set.of(
            "identity", "students", "catalog", "scheduling", "bookings", "notifications", "media", "coachprofile");
    private static final Pattern REFERENCE = Pattern.compile(
            "(?:TODO|ADR|PLAN)-[0-9]{4}|ai-docs/(?:implement-plan|decisions|todo)/[A-Za-z0-9./-]+\\.md");

    enum Category {
        DOMAIN_PURITY, PORT_PURITY, APPLICATION_BOUNDARY,
        INBOUND_BOUNDARY, CROSS_MODULE_INTERNAL, MODULE_CYCLE
    }

    record Violation(Category category, String id, Set<String> ruleIds) {
        Violation {
            ruleIds = Set.copyOf(ruleIds);
        }
    }

    record ExceptionEntry(String id, String reason, String reference, String removalCondition) {}

    private ArchitectureBaseline() {}

    static Set<Violation> scan(JavaClasses classes) {
        Map<String, Violation> violations = new TreeMap<>();
        Map<String, Set<String>> moduleEdges = new TreeMap<>();
        for (JavaClass source : classes) {
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String from = source.getName();
                String to = dependency.getTargetClass().getName();
                String fromModule = topPackage(from);
                String toModule = topPackage(to);
                if (fromModule != null && toModule != null && !fromModule.equals(toModule)) {
                    moduleEdges.computeIfAbsent(fromModule, unused -> new TreeSet<>()).add(toModule);
                }
                Set<String> rules = new TreeSet<>();
                Category category = categoryFor(from, to, rules);
                if (category == null) continue;
                String id = category + ":" + from + "->" + to;
                Violation previous = violations.get(id);
                if (previous != null) rules.addAll(previous.ruleIds());
                violations.put(id, new Violation(category, id, rules));
            }
        }
        for (Set<String> component : cyclicComponents(moduleEdges)) {
            Set<String> edges = new TreeSet<>();
            for (String from : component) {
                for (String to : moduleEdges.getOrDefault(from, Set.of())) {
                    if (component.contains(to)) edges.add(from + "->" + to);
                }
            }
            String id = Category.MODULE_CYCLE + ":modules=" + String.join(",", new TreeSet<>(component))
                    + ";edges=" + String.join(",", edges);
            violations.put(id, new Violation(Category.MODULE_CYCLE, id, Set.of("DEP-06")));
        }
        return Set.copyOf(violations.values());
    }

    private static Category categoryFor(String from, String to, Set<String> rules) {
        String sourceModule = businessModule(from);
        String targetModule = businessModule(to);
        boolean crossModuleInternal = sourceModule != null && targetModule != null
                && !sourceModule.equals(targetModule)
                && !to.startsWith(ROOT + targetModule + ".application.port.in.");
        boolean domain = from.contains(".domain.") && (to.startsWith(ROOT) &&
                (to.contains(".application.") || to.contains(".adapter.")) || externalMechanism(to));
        boolean port = from.contains(".application.port.") &&
                (to.contains(".adapter.") || to.contains(".application.service.") || externalMechanism(to));
        boolean application = from.contains(".application.") &&
                (to.contains(".adapter.") || from.contains(".application.service.") && serviceMechanism(to));
        boolean inbound = from.contains(".adapter.in.") &&
                (to.contains(".adapter.out.") || to.contains(".application.service."));
        if (crossModuleInternal) rules.add("DEP-04");
        if (domain) rules.addAll(Set.of("ARCH-02", "ARCH-05", "DEP-01"));
        if (port) rules.add("DEP-01");
        if (application) rules.addAll(Set.of("ARCH-03", "ARCH-05", "DEP-02"));
        if (inbound) rules.addAll(Set.of("ARCH-04", "ARCH-05", "DEP-03"));
        // One edge has one primary category, while ruleIds retains every rule it violates.
        if (crossModuleInternal) return Category.CROSS_MODULE_INTERNAL;
        if (domain) return Category.DOMAIN_PURITY;
        if (port) return Category.PORT_PURITY;
        if (application) return Category.APPLICATION_BOUNDARY;
        if (inbound) return Category.INBOUND_BOUNDARY;
        return null;
    }

    private static boolean externalMechanism(String name) {
        return name.startsWith("org.springframework.") || name.startsWith("jakarta.")
                || name.startsWith("javax.") || serviceMechanism(name);
    }

    private static boolean serviceMechanism(String name) {
        return name.startsWith("java.sql.") || name.startsWith("org.apache.ibatis.")
                || name.startsWith("software.amazon.awssdk.")
                || name.startsWith("org.springframework.web.")
                || name.startsWith("org.springframework.security.")
                || name.startsWith("org.springframework.jdbc.")
                || name.startsWith("jakarta.servlet.")
                || name.startsWith("jakarta.persistence.");
    }

    private static String businessModule(String className) {
        String top = topPackage(className);
        return top != null && BUSINESS_MODULES.contains(top) ? top : null;
    }

    private static String topPackage(String className) {
        if (!className.startsWith(ROOT)) return null;
        String rest = className.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? null : rest.substring(0, dot);
    }

    private static Set<Set<String>> cyclicComponents(Map<String, Set<String>> edges) {
        Set<String> nodes = new TreeSet<>(edges.keySet());
        edges.values().forEach(nodes::addAll);
        Set<Set<String>> components = new HashSet<>();
        for (String node : nodes) {
            Set<String> reachable = reachable(node, edges);
            Set<String> component = new TreeSet<>();
            for (String candidate : reachable) {
                if (reachable(candidate, edges).contains(node)) component.add(candidate);
            }
            if (component.size() > 1) components.add(Set.copyOf(component));
        }
        return components;
    }

    private static Set<String> reachable(String start, Map<String, Set<String>> edges) {
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(edges.getOrDefault(start, Set.of()));
        while (!queue.isEmpty()) {
            String next = queue.removeFirst();
            if (seen.add(next)) queue.addAll(edges.getOrDefault(next, Set.of()));
        }
        return seen;
    }

    static void verify(Set<Violation> actual, Map<Category, Integer> expectedCounts,
                       Set<ExceptionEntry> exceptions) {
        List<String> errors = new ArrayList<>();
        if (!expectedCounts.keySet().equals(Set.of(Category.values())))
            errors.add("baseline must explicitly declare every category exactly once");
        Map<Category, Integer> registeredCounts = new EnumMap<>(Category.class);
        for (Category category : Category.values()) registeredCounts.put(category, 0);
        Map<String, ExceptionEntry> registered = new HashMap<>();
        for (ExceptionEntry entry : exceptions) {
            if (entry.id() == null || entry.id().isBlank() || registered.putIfAbsent(entry.id(), entry) != null)
                errors.add("duplicate or missing exception identity: " + entry.id());
            Category category = categoryOf(entry.id());
            if (category == null) errors.add("unknown category in exception: " + entry.id());
            else registeredCounts.merge(category, 1, Integer::sum);
            if (!specific(entry.reason()) || entry.reference() == null
                    || !REFERENCE.matcher(entry.reference()).matches() || !specific(entry.removalCondition()))
                errors.add("incomplete reason, linked plan/ADR/ticket or removal condition: " + entry.id());
        }
        for (Category category : Category.values()) {
            Integer count = expectedCounts.get(category);
            if (count == null || count < 0 || count != registeredCounts.get(category))
                errors.add(category + " baseline=" + count + ", registered=" + registeredCounts.get(category));
        }
        Map<String, Violation> actualById = new TreeMap<>();
        for (Violation violation : actual) {
            if (violation.category() == null || violation.id() == null
                    || !violation.id().startsWith(violation.category() + ":") || violation.ruleIds().isEmpty())
                errors.add("malformed violation: " + violation);
            if (actualById.putIfAbsent(violation.id(), violation) != null)
                errors.add("duplicate actual violation: " + violation.id());
        }
        for (Violation violation : actualById.values()) {
            if (!registered.containsKey(violation.id()))
                errors.add("new " + violation.category() + " violation " + violation.id()
                        + " rules=" + violation.ruleIds() + " baseline=" + expectedCounts.get(violation.category()));
        }
        for (String id : registered.keySet()) {
            if (!actualById.containsKey(id)) errors.add("stale exception " + id + "; remove it and decrement baseline");
        }
        if (!errors.isEmpty()) throw new AssertionError(String.join("\n", errors));
    }

    private static Category categoryOf(String id) {
        if (id == null) return null;
        int separator = id.indexOf(':');
        if (separator < 0) return null;
        try { return Category.valueOf(id.substring(0, separator)); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static boolean specific(String value) {
        if (value == null) return false;
        String trimmed = value.strip();
        if (trimmed.length() < 15) return false;
        String upper = trimmed.toUpperCase();
        return !(upper.startsWith("TODO") || upper.startsWith("TBD")
                || upper.startsWith("N/A") || upper.startsWith("PLACEHOLDER")
                || upper.startsWith("FIXME"));
    }
}
