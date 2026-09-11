package edu.seu.vcampus.server.course.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Validates curriculum prerequisite edges and returns a stable prerequisite-first order. */
public final class CurriculumGraphValidator {
    private CurriculumGraphValidator() {}

    public static List<String> validate(Map<String, ? extends Set<String>> prerequisites) {
        Map<String, State> states = new HashMap<>();
        List<String> ordered = new ArrayList<>();
        Set<String> nodes = new TreeSet<>(prerequisites.keySet());
        prerequisites.values().forEach(nodes::addAll);
        for (String node : nodes) visit(node, prerequisites, states, ordered);
        return List.copyOf(ordered);
    }

    private static void visit(String node, Map<String, ? extends Set<String>> prerequisites,
                              Map<String, State> states, List<String> ordered) {
        State state = states.get(node);
        if (state == State.DONE) return;
        if (state == State.VISITING) {
            throw new IllegalArgumentException("Prerequisite cycle detected at course " + node);
        }
        states.put(node, State.VISITING);
        Set<String> required = prerequisites.get(node);
        for (String prerequisite : new TreeSet<>(required == null ? Set.of() : required)) {
            visit(prerequisite, prerequisites, states, ordered);
        }
        states.put(node, State.DONE);
        ordered.add(node);
    }

    private enum State { VISITING, DONE }
}
