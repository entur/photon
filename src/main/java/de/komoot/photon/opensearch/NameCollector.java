package de.komoot.photon.opensearch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Collects strings with a priorities, discarding duplicate strings while
 * keeping the highest priority.
 */
public class NameCollector {

    private final Map<String, Integer> terms = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();

    public NameCollector() {
    }

    public NameCollector(Collection<String> termCollection) {
        addAll(termCollection, 1);
    }

    public void add(String term, int searchPrio) {
        if (term == null || term.isBlank()) {
            LOGGER.warn("FOO: term is null or empty");
            return;
        }
        final var cleaned = term.replace("|", " ");
        final var currentPrio = terms.get(cleaned);
        if (currentPrio == null || currentPrio < searchPrio) {
            terms.put(cleaned, Integer.max(searchPrio, 1));
        }
    }

    public void addAll(Collection<String> termCollection, int searchPrio) {
        for (var term : termCollection) {
            add(term, searchPrio);
        }
    }

    public String toCollectorString() {
        return terms.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(e -> String.format("%s|%d", e.getKey(), e.getValue()))
                .collect(Collectors.joining(";"));
    }
}
