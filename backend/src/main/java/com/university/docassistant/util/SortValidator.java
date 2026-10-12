package com.university.docassistant.util;

import com.university.docassistant.exception.ApiException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Restricts {@code ?sort=} to known fields: an unknown one would otherwise fail deep in the query as a 500,
 * and sorting by an arbitrary column of a large table is expensive.
 */
public final class SortValidator {

    private SortValidator() {
    }

    public static Pageable requireSortable(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw ApiException.badRequest("Cannot sort by '" + order.getProperty() + "'; allowed: " + allowed);
            }
        }
        return pageable;
    }
}
