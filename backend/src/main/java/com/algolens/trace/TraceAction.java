package com.algolens.trace;

/**
 * What a single step of the program mainly did. The frontend keys its animation and its
 * step label off this value, so it must stay language independent: a Python or C++ executor
 * added later emits exactly these actions.
 */
public enum TraceAction {

    /** Synthetic first step, showing initial state before anything runs. */
    START,

    /** A statement ran that does not fit any more specific action. */
    STATEMENT,

    /** A variable came into existence. */
    DECLARE,

    /** A scalar variable was assigned. */
    ASSIGN,

    /** One or more array cells were read. */
    ARRAY_READ,

    /** One or more array cells were written. */
    ARRAY_WRITE,

    /** An object field was read, e.g. {@code node.next}. */
    FIELD_READ,

    /** An object field was assigned, e.g. {@code node.next = other}. */
    FIELD_WRITE,

    /** A new object was created. */
    ALLOCATE,

    /** Two array cells were compared against each other. */
    COMPARE,

    /** Two array cells exchanged values (collapsed from the classic temp-variable dance). */
    SWAP,

    /** A branch or loop condition was evaluated. */
    CONDITION,

    /** A user-defined method was entered. */
    CALL,

    /** A user-defined method returned. */
    RETURN,

    /** The program printed something. */
    OUTPUT,

    /** Synthetic final step, showing the end state. */
    DONE,

    /** Execution stopped here because of an error. */
    ERROR
}
