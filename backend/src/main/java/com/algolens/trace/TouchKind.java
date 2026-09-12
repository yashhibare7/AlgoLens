package com.algolens.trace;

/** How a step interacted with a particular cell. */
public enum TouchKind {
    READ,
    WRITE,
    COMPARE,
    SWAP
}
