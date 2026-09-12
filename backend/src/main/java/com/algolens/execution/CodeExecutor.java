package com.algolens.execution;

import com.algolens.entity.Language;
import com.algolens.trace.ExecutionTrace;

/**
 * The seam every language plugs into.
 *
 * <p>An implementation turns source text into an {@link ExecutionTrace} and nothing else. It does
 * not know about HTTP, users, credits or persistence, and crucially the frontend does not know
 * which implementation produced a trace. Adding Python means adding one class here; the
 * visualizers, playback controls and history pages are untouched.
 *
 * <p><b>Contract:</b> an implementation must never throw for bad user input. Compile errors,
 * runtime errors, timeouts and limit overruns are all reported as an {@link ExecutionTrace} with
 * the matching {@link com.algolens.entity.ExecutionStatus} and a message the user can act on. Only
 * genuine bugs in the executor itself should escape.
 */
public interface CodeExecutor {

    Language language();

    ExecutionTrace execute(ExecutionRequest request);
}
