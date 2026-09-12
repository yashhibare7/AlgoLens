package com.algolens.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Page parameters with a hard ceiling. Without the cap, {@code ?size=100000} is a trivially
 * available way to make the server serialise a user's entire history into one response.
 */
final class Pagination {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private Pagination() {
    }

    static Pageable of(Integer page, Integer size) {
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(resolvedPage, resolvedSize);
    }
}
