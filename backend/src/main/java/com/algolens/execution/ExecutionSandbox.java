package com.algolens.execution;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs one execution off the request thread, with a hard wall-clock backstop.
 *
 * <p>This is the isolation boundary, and it is deliberately thin because the heavy lifting
 * happens elsewhere: user code is interpreted, not executed, so there is no child process, no
 * classloading, and no reachable syntax for I/O. What is left to defend against is a submission
 * that simply takes too long or nests too deeply, which is what this class handles:
 *
 * <ul>
 *   <li>a dedicated thread per execution, so a hung run cannot block Tomcat's worker;</li>
 *   <li>a large explicit stack, so deep-but-legitimate recursion works and pathological nesting
 *       raises {@link StackOverflowError} on a thread that is not serving HTTP;</li>
 *   <li>a timeout that is a backstop only -- {@code ExecutionBudget} inside the interpreter is
 *       the primary stop, and it is what makes the timeout cooperative.</li>
 * </ul>
 *
 * <p><b>Before this service accepts real code execution</b> (a JVM-backed Java executor, or the
 * Python and C++ executors on the roadmap) this class must be replaced by a real sandbox: a
 * container per run with CPU, memory, PID and file-descriptor limits, no network namespace and a
 * read-only root filesystem. The interface stays the same, which is the point -- see
 * {@code docs/ARCHITECTURE.md}.
 */
@Component
public class ExecutionSandbox {

    private static final Logger log = LoggerFactory.getLogger(ExecutionSandbox.class);

    /** 16 MB: enough for recursion depths a DSA exercise plausibly needs. */
    private static final int STACK_SIZE_BYTES = 16 * 1024 * 1024;

    /** Grace on top of the interpreter's own deadline before we stop waiting. */
    private static final long GRACE_MS = 2_000;

    private final AtomicLong counter = new AtomicLong();
    private final java.util.concurrent.ExecutorService pool;

    public ExecutionSandbox() {
        this.pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(null, runnable,
                    "algolens-exec-" + counter.incrementAndGet(), STACK_SIZE_BYTES);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * @param task            the execution to run
     * @param budgetTimeoutMs the interpreter's own wall-clock budget
     * @return the task result
     * @throws SandboxTimeoutException when the task outlived its budget plus the grace period
     */
    public <T> T run(Callable<T> task, long budgetTimeoutMs) {
        Future<T> future = pool.submit(task);
        try {
            return future.get(budgetTimeoutMs + GRACE_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Execution exceeded {} ms and was abandoned", budgetTimeoutMs + GRACE_MS);
            throw new SandboxTimeoutException(
                    "Execution took longer than " + budgetTimeoutMs + " ms and was stopped");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new SandboxTimeoutException("Execution was interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Execution failed", cause);
        }
    }

    /** Raised when an execution had to be abandoned rather than completing. */
    public static class SandboxTimeoutException extends RuntimeException {
        public SandboxTimeoutException(String message) {
            super(message);
        }
    }
}
