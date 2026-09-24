package com.codgo.ulock.sharedkernel.paging;

import java.util.List;
import java.util.Objects;

/** Framework-free pagination request: zero-based page, page size and sort order. */
public record PageQuery(int page, int size, List<Sort> sort) {

    public enum Direction { ASC, DESC }

    public record Sort(String property, Direction direction) {

        public Sort {
            Objects.requireNonNull(property, "property");
            Objects.requireNonNull(direction, "direction");
        }
    }

    public PageQuery {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size >= 1");
        }
        sort = List.copyOf(sort);
    }
}
