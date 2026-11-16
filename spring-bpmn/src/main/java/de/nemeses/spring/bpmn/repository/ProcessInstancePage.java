package de.nemeses.spring.bpmn.repository;

import java.util.List;

/**
 * One page of the result of {@link ProcessInstanceRepository#query}.
 *
 * @param items the instances of the page, in the requested sort order
 * @param total the number of instances matching the query on all pages
 */
public record ProcessInstancePage(List<ProcessInstance> items, long total) {

    /**
     * Copies the items.
     */
    public ProcessInstancePage {
        items = List.copyOf(items);
    }
}
