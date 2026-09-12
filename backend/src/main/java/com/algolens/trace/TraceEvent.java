package com.algolens.trace;

import java.util.List;

/**
 * One row of the execution trace: everything the UI needs to render a single step.
 *
 * <p>The event describes the state <em>after</em> the statement on {@link #line} finished,
 * annotated with what that statement touched. That is deliberately the same mental model as a
 * step debugger: press Next, the highlighted line has just run, and the picture shows the
 * result.
 *
 * @param step          1-based index into the trace (0 is the synthetic START step)
 * @param line          1-based source line this step executed
 * @param action        the dominant thing this step did
 * @param message       human readable sentence, e.g. "Compared arr[0]=5 and arr[1]=2"
 * @param variables     scalar locals of the active frame plus static fields
 * @param visualizations every drawable structure in scope, already highlighted
 * @param callStack     innermost frame last
 * @param touches       raw cell interactions for this step
 * @param output        text this step printed, or null
 * @param depth         call depth of the active frame (0 = entry point)
 */
public record TraceEvent(
        int step,
        int line,
        TraceAction action,
        String message,
        List<VariableValue> variables,
        List<VisualizationState> visualizations,
        List<StackFrameState> callStack,
        List<Touch> touches,
        String output,
        int depth) {

    public TraceEvent relabelled(TraceAction newAction, String newMessage) {
        return new TraceEvent(step, line, newAction, newMessage, variables, visualizations,
                callStack, touches, output, depth);
    }

    public TraceEvent withTouches(List<Touch> newTouches, List<VisualizationState> newVisualizations) {
        return new TraceEvent(step, line, action, message, variables, newVisualizations, callStack,
                newTouches, output, depth);
    }
}
