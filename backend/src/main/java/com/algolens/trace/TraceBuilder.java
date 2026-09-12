package com.algolens.trace;

import com.algolens.entity.ExecutionStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Accumulates a trace while an executor runs.
 *
 * <p>Usage is an open/annotate/commit cycle, once per executed statement:
 *
 * <pre>
 *   builder.begin(line);
 *   ... note(...) / touch(...) / print(...) while the statement runs
 *   builder.commit(variables, visualizations, callStack, depth);
 * </pre>
 *
 * <p>Statements nest, because a statement can contain a call whose body is more statements. So
 * the staging area is a <em>stack</em>: annotations land on the innermost open statement, and an
 * outer statement's event is emitted after the inner ones it caused. Combined with the
 * {@link #synthetic} CALL/RETURN markers the executor emits around a call, the resulting order
 * reads the way a debugger does -- call site, callee body, return, then the call site's result.
 *
 * <p>{@code note} keeps the most specific action reported during a statement (SWAP beats
 * ARRAY_WRITE beats COMPARE beats a plain STATEMENT), so the label describes the interesting
 * thing that happened rather than whichever thing happened last.
 *
 * <p>Not thread safe: one builder belongs to one execution on one thread.
 */
public class TraceBuilder {

    /** One statement's annotations while it is still running. */
    private static final class Pending {
        private final int line;
        private TraceAction action = TraceAction.STATEMENT;
        private String message;
        private final List<Touch> touches = new ArrayList<>(4);
        private StringBuilder output;

        private Pending(int line) {
            this.line = line;
        }
    }

    private final int maxEvents;
    private final int maxOutputChars;

    private final List<TraceEvent> events = new ArrayList<>();
    private final StringBuilder stdout = new StringBuilder();
    private final Deque<Pending> staging = new ArrayDeque<>();

    private boolean truncated;

    // ---- metrics ----
    private long comparisons;
    private long arrayReads;
    private long arrayWrites;
    private long fieldReads;
    private long fieldWrites;
    private long objectsCreated;
    private long calls;
    private int maxCallDepth;

    public TraceBuilder(int maxEvents, int maxOutputChars) {
        this.maxEvents = maxEvents;
        this.maxOutputChars = maxOutputChars;
    }

    public void begin(int sourceLine) {
        staging.push(new Pending(sourceLine));
    }

    public boolean isOpen() {
        return !staging.isEmpty();
    }

    /** Reports something the innermost running statement did. The most specific report wins. */
    public void note(TraceAction candidate, String candidateMessage) {
        Pending pending = staging.peek();
        if (pending == null) {
            return;
        }
        if (rank(candidate) >= rank(pending.action)) {
            pending.action = candidate;
            pending.message = candidateMessage;
        }
    }

    public void touch(Touch touch) {
        Pending pending = staging.peek();
        if (pending != null) {
            pending.touches.add(touch);
        }
    }

    public void print(String text) {
        if (stdout.length() + text.length() > maxOutputChars) {
            truncated = true;
            throw new TraceLimitExceededException(
                    "Program produced more than " + maxOutputChars + " characters of output");
        }
        stdout.append(text);
        Pending pending = staging.peek();
        if (pending != null) {
            if (pending.output == null) {
                pending.output = new StringBuilder();
            }
            pending.output.append(text);
            note(TraceAction.OUTPUT, "Printed: " + text.strip());
        }
    }

    public void commit(List<VariableValue> variables, List<VisualizationState> visualizations,
            List<StackFrameState> callStack, int depth) {
        Pending pending = staging.poll();
        if (pending == null) {
            return;
        }
        ensureCapacity();
        List<Touch> touches = pending.touches.isEmpty() ? null : List.copyOf(pending.touches);
        events.add(new TraceEvent(
                events.size(),
                pending.line,
                pending.action,
                pending.message != null ? pending.message : defaultMessage(pending.action),
                variables,
                Highlighter.apply(visualizations, touches),
                callStack,
                touches,
                pending.output == null ? null : pending.output.toString(),
                depth));
        maxCallDepth = Math.max(maxCallDepth, depth);
    }

    /** Drops the innermost statement in progress without recording it. */
    public void abandon() {
        staging.poll();
    }

    /** Records a step that is not tied to a statement: START, CALL, RETURN, DONE, ERROR. */
    public void synthetic(TraceAction action, int sourceLine, String message,
            List<VariableValue> variables, List<VisualizationState> visualizations,
            List<StackFrameState> callStack, int depth) {
        if (events.size() >= maxEvents) {
            truncated = true;
            return;
        }
        events.add(new TraceEvent(events.size(), sourceLine, action, message, variables,
                visualizations, callStack, null, null, depth));
        maxCallDepth = Math.max(maxCallDepth, depth);
    }

    private void ensureCapacity() {
        if (events.size() >= maxEvents) {
            truncated = true;
            throw new TraceLimitExceededException(
                    "Execution produced more than " + maxEvents + " visualisable steps");
        }
    }

    public void countComparison() {
        comparisons++;
    }

    public void countArrayRead() {
        arrayReads++;
    }

    public void countArrayWrite() {
        arrayWrites++;
    }

    public void countFieldRead() {
        fieldReads++;
    }

    public void countFieldWrite() {
        fieldWrites++;
    }

    public void countObjectCreated() {
        objectsCreated++;
    }

    public void countCall() {
        calls++;
    }

    public void markTruncated() {
        truncated = true;
    }

    public int eventCount() {
        return events.size();
    }

    public String stdout() {
        return stdout.toString();
    }

    public ExecutionTrace build(String language, ExecutionStatus status, String errorMessage,
            Integer errorLine, long durationMs) {
        List<TraceEvent> collapsed = SwapDetector.collapse(events);
        long swaps = SwapDetector.countSwaps(collapsed);
        TraceMetrics metrics = new TraceMetrics(collapsed.size(), comparisons, swaps, arrayReads,
                arrayWrites, fieldReads, fieldWrites, objectsCreated, calls, maxCallDepth);
        return new ExecutionTrace(language, status, collapsed, collapsed.size(), truncated,
                stdout.toString(), errorMessage, errorLine, durationMs, metrics);
    }

    private static String defaultMessage(TraceAction action) {
        return switch (action) {
            case START -> "Initial state";
            case DONE -> "Program finished";
            case CONDITION -> "Evaluated condition";
            case DECLARE -> "Declared a variable";
            case ASSIGN -> "Assigned a variable";
            case ARRAY_READ -> "Read from array";
            case ARRAY_WRITE -> "Wrote to array";
            case FIELD_READ -> "Read an object field";
            case FIELD_WRITE -> "Wrote an object field";
            case ALLOCATE -> "Created an object";
            case COMPARE -> "Compared two values";
            case SWAP -> "Swapped two values";
            case CALL -> "Called a method";
            case RETURN -> "Returned from a method";
            case OUTPUT -> "Printed output";
            case ERROR -> "Execution stopped";
            case STATEMENT -> "Executed statement";
        };
    }

    private static int rank(TraceAction action) {
        return switch (action) {
            case ERROR -> 100;
            case SWAP -> 8;
            case ARRAY_WRITE, FIELD_WRITE -> 7;
            case COMPARE -> 6;
            case OUTPUT, CALL, RETURN -> 5;
            case ARRAY_READ, FIELD_READ -> 4;
            case ASSIGN -> 3;
            // ALLOCATE ties with DECLARE so that `Node head = new Node(1)` reports the
            // declaration -- the later, more informative note wins a tie.
            case DECLARE, ALLOCATE -> 2;
            case CONDITION -> 1;
            case START, DONE, STATEMENT -> 0;
        };
    }
}
