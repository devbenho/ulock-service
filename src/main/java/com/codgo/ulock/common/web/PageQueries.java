package com.codgo.ulock.common.web;

import com.codgo.ulock.sharedkernel.paging.PageQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Converts Spring MVC pagination parameters into the framework-free {@link PageQuery}. */
public final class PageQueries {

    private PageQueries() {}

    public static PageQuery from(Pageable pageable) {
        return new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().stream()
                .map(order -> new PageQuery.Sort(order.getProperty(),
                        order.getDirection() == Sort.Direction.ASC ? PageQuery.Direction.ASC : PageQuery.Direction.DESC))
                .toList());
    }
}
