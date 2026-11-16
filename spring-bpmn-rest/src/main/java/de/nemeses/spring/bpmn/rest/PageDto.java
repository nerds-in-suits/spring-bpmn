package de.nemeses.spring.bpmn.rest;

import java.util.List;

/**
 * One page of a list response.
 *
 * @param <T> the type of the items
 * @param items the items of the page, in the requested order
 * @param page the 0-based index of the page
 * @param size the page size used, at most {@code spring.bpmn.rest.max-page-size}
 * @param total the number of items on all pages
 */
public record PageDto<T>(List<T> items, int page, int size, long total) {

    /**
     * Copies the items.
     */
    public PageDto {
        items = List.copyOf(items);
    }
}
