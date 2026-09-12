package com.algolens.execution.interpreter;

import com.algolens.trace.ArrayElement;
import com.algolens.trace.ElementState;
import com.algolens.trace.Pointer;
import com.algolens.trace.StackFrameState;
import com.algolens.trace.Touch;
import com.algolens.trace.TraceAction;
import com.algolens.trace.TraceBuilder;
import com.algolens.trace.VariableValue;
import com.algolens.trace.VisualizationState;
import com.algolens.trace.VisualizationType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tree-walking interpreter for the supported Java subset, instrumented to emit a
 * {@link com.algolens.trace.ExecutionTrace} as it goes.
 *
 * <p><b>Why interpret instead of compiling and running?</b> The product needs a step-by-step
 * trace with variable, array and object state at every line. Getting that from a real JVM means
 * either a JDI debugger attached to a sandboxed process or bytecode instrumentation -- both of
 * which run arbitrary user code and inherit every escape, resource-exhaustion and side-effect
 * problem that comes with it. Interpreting a deliberately small subset instead means user code
 * never becomes machine code: there is no process to escape from, no filesystem or network
 * reachable because no syntax names them, and every loop iteration passes through
 * {@link ExecutionBudget}. The trace falls out of evaluation for free rather than being
 * reconstructed after the fact.
 *
 * <p>The cost is honest and bounded: only the subset runs. {@link Parser} and {@link Builtins}
 * reject everything else with a message naming what is missing, and
 * {@code docs/SUPPORTED_JAVA_SUBSET.md} is the contract.
 *
 * <p>One instance interprets one program once, on one thread.
 */
public final class Interpreter {

    /**
     * Variable names rendered as index arrows under an array. Restricting pointers to
     * conventional index names keeps the picture readable -- drawing an arrow for every in-range
     * int would bury the two that matter under a dozen that do not. Object pointers have no such
     * restriction: a reference variable unambiguously points at one node.
     */
    private static final Set<String> POINTER_NAMES = Set.of(
            "i", "j", "k", "p", "q", "low", "high", "mid", "lo", "hi", "left", "right", "start",
            "end", "first", "last", "pivot", "pivotIndex", "minIndex", "maxIndex",
            "index", "idx", "pos", "cur", "curr", "current", "slow", "fast");

    /** Reference-field name sets that identify a shape. Anything else renders as a graph. */
    private static final Set<String> LIST_FIELDS = Set.of("next", "prev", "previous");
    private static final Set<String> TREE_FIELDS = Set.of("left", "right", "parent");

    /** Bounds the object graph a single step can describe, so payloads stay finite. */
    private static final int MAX_GRAPH_NODES = 300;

    /** One call frame. {@code scope} moves as blocks open and close inside the frame. */
    private static final class Frame {
        private final String method;
        private Environment scope;
        private int line;
        /** The receiver for an instance method, or null in a static context. */
        private final ObjectValue self;

        private Frame(String method, Environment root, int line, ObjectValue self) {
            this.method = method;
            this.scope = root;
            this.line = line;
            this.self = self;
        }
    }

    // Control flow implemented as exceptions, with stack traces disabled: these are thrown per
    // loop iteration, and filling in a stack trace each time would dominate the runtime.
    private abstract static class Signal extends RuntimeException {
        Signal() {
            super(null, null, false, false);
        }
    }

    private static final class BreakSignal extends Signal {
        private static final BreakSignal INSTANCE = new BreakSignal();
    }

    private static final class ContinueSignal extends Signal {
        private static final ContinueSignal INSTANCE = new ContinueSignal();
    }

    private static final class ReturnSignal extends Signal {
        private final Object value;

        private ReturnSignal(Object value) {
            this.value = value;
        }
    }

    /** A {@code throw} in user code, travelling up to the nearest matching {@code catch}. */
    private static final class ThrowSignal extends Signal {
        private final ThrownValue thrown;

        private ThrowSignal(ThrownValue thrown) {
            this.thrown = thrown;
        }
    }

    /**
     * Exception classes user code can construct with {@code new}.
     *
     * <p>The subset has no inheritance, so a user-defined {@code class MyException extends
     * Exception} cannot work. These built-in names cover what algorithm code actually throws.
     */
    private static final Set<String> EXCEPTION_TYPES = Set.of(
            "Exception", "RuntimeException", "Throwable", "Error",
            "IllegalArgumentException", "IllegalStateException", "UnsupportedOperationException",
            "ArithmeticException", "NullPointerException", "NumberFormatException",
            "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException",
            "StringIndexOutOfBoundsException", "NoSuchElementException",
            "ClassCastException", "NegativeArraySizeException");

    private final Ast.Program program;
    private final TraceBuilder trace;
    private final ExecutionBudget budget;

    private final List<Ast.MethodDecl> methods = new ArrayList<>();
    private final Map<String, Ast.ClassDecl> classes = new LinkedHashMap<>();
    private final Environment globals = new Environment(null);
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<Set<String>> changedNames = new ArrayDeque<>();

    private int nextObjectId = 1;

    /**
     * The empty {@code String[]} handed to {@code main}. Tracked by identity so it can be kept
     * out of the visualization: it is a value AlgoLens invented to satisfy the signature, and
     * drawing an empty {@code args} array on every step of every run is pure clutter.
     */
    private ArrayValue syntheticArgs;

    public Interpreter(Ast.Program program, TraceBuilder trace, ExecutionBudget budget) {
        this.program = program;
        this.trace = trace;
        this.budget = budget;
    }

    // ------------------------------------------------------------------ entry point

    /**
     * Runs the program, recording every step into the trace builder.
     *
     * @throws InterpreterException    on a runtime failure in the interpreted code
     * @throws BudgetExceededException when a hard limit is hit
     */
    public void run() {
        indexDeclarations();

        String entryName = program.entryMethod();
        Ast.MethodDecl entry = entryName == null ? null : findMethod(methods, entryName, 1);
        if (entry == null && entryName != null) {
            entry = findAnyByName(methods, entryName);
        }
        int startLine = entry != null ? entry.line()
                : program.statements().isEmpty() ? 1 : program.statements().get(0).line();

        frames.push(new Frame(entry != null ? entry.name() : "main", new Environment(null),
                startLine, null));
        defineStaticFields();

        trace.synthetic(TraceAction.START, startLine, "Initial state", snapshotVariables(),
                snapshotVisualizations(), snapshotCallStack(), 0);

        try {
            runEntryPoint(entry);
            trace.synthetic(TraceAction.DONE, currentLine(), "Program finished",
                    snapshotVariables(), snapshotVisualizations(), snapshotCallStack(), 0);
        } catch (RuntimeException failure) {
            // Close any half-finished statements, then record where execution stopped so the UI
            // can show the failing line with the state that caused it.
            while (trace.isOpen()) {
                trace.abandon();
            }
            trace.synthetic(TraceAction.ERROR, currentLine(), describeFailure(failure),
                    snapshotVariables(), snapshotVisualizations(), snapshotCallStack(),
                    Math.max(0, frames.size() - 1));
            throw failure;
        }
    }

    /**
     * Runs the entry point, converting any control-flow signal that escapes it into a proper
     * error.
     *
     * <p>An uncaught {@code throw} is the important case: the signal carrying it is an internal
     * mechanism, and letting it escape would surface as "AlgoLens hit an internal error" when
     * the truth is simply that the program threw. It becomes a normal runtime error with the
     * exception's own message instead.
     */
    private void runEntryPoint(Ast.MethodDecl entry) {
        try {
            if (entry != null) {
                for (Ast.Param parameter : entry.parameters()) {
                    frames.peek().scope.declare(parameter.name(), parameter.type(),
                            entryArgument(parameter.type()));
                }
                // The entry method's body runs directly in the frame's own scope rather than a
                // nested block scope. Otherwise its locals go out of scope the instant the body
                // ends, and the DONE step -- the finished picture people actually want to look
                // at -- would show nothing at all.
                for (Ast.Stmt statement : entry.body().statements()) {
                    execute(statement);
                }
            } else {
                for (Ast.Stmt statement : program.statements()) {
                    execute(statement);
                }
            }
        } catch (ReturnSignal ignored) {
            // main returned early; nothing to collect
        } catch (ThrowSignal signal) {
            throw InterpreterException.thrown(signal.thrown.type(), signal.thrown.message(),
                    currentLine());
        } catch (BreakSignal | ContinueSignal signal) {
            throw new InterpreterException(
                    "'break' or 'continue' used outside of a loop or switch", currentLine());
        }
    }

    /** The line the interpreter was last on, used for error reporting. */
    public int currentLine() {
        Frame frame = frames.peek();
        return frame == null ? 0 : frame.line;
    }

    private static String describeFailure(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private void indexDeclarations() {
        methods.addAll(program.methods());
        rejectDuplicateArities(methods, "method");

        for (Ast.ClassDecl declaration : program.classes()) {
            classes.put(declaration.name(), declaration);
            rejectDuplicateArities(declaration.methods(), "method");
            rejectDuplicateArities(declaration.constructors(), "constructor");
        }
    }

    /**
     * Overloads resolve on argument count alone, so two overloads with the same arity are
     * genuinely ambiguous here and are rejected up front rather than silently mis-dispatched.
     */
    private static void rejectDuplicateArities(List<Ast.MethodDecl> candidates, String kind) {
        Set<String> seen = new HashSet<>();
        for (Ast.MethodDecl candidate : candidates) {
            if (!seen.add(candidate.name() + "/" + candidate.arity())) {
                throw new SyntaxException(
                        "Two " + kind + "s named '" + candidate.name() + "' take "
                                + candidate.arity() + " argument(s). Overloads are resolved by "
                                + "argument count, so these cannot be told apart.",
                        candidate.line());
            }
        }
    }

    private void defineStaticFields() {
        for (Ast.VarDecl field : program.fields()) {
            for (Ast.Declarator declarator : field.declarators()) {
                String type = declaredTypeOf(field, declarator);
                Object value = declarator.initializer() == null
                        ? Values.defaultValue(type)
                        : Values.coerce(type, evaluate(declarator.initializer()),
                                "to '" + declarator.name() + "'", field.line());
                nameContainer(value, declarator.name());
                globals.declare(declarator.name(), type, value);
            }
        }
    }

    private static String declaredTypeOf(Ast.VarDecl declaration, Ast.Declarator declarator) {
        return declaration.type() + "[]".repeat(declarator.extraDimensions());
    }

    private Object entryArgument(String type) {
        // 'main' is invoked with an empty String[] so 'args.length' behaves sensibly.
        if (!"String[]".equals(type)) {
            return Values.defaultValue(type);
        }
        syntheticArgs = new ArrayValue("String", new Object[0]);
        return syntheticArgs;
    }

    // ------------------------------------------------------------------ statements

    private void execute(Ast.Stmt statement) {
        if (statement instanceof Ast.Block block) {
            executeBlock(block);
        } else if (statement instanceof Ast.VarDecl declaration) {
            step(declaration.line(), () -> declareLocals(declaration));
        } else if (statement instanceof Ast.ExprStmt expressionStatement) {
            step(expressionStatement.line(), () -> evaluate(expressionStatement.expression()));
        } else if (statement instanceof Ast.If branch) {
            executeIf(branch);
        } else if (statement instanceof Ast.While loop) {
            executeWhile(loop);
        } else if (statement instanceof Ast.DoWhile loop) {
            executeDoWhile(loop);
        } else if (statement instanceof Ast.For loop) {
            executeFor(loop);
        } else if (statement instanceof Ast.ForEach loop) {
            executeForEach(loop);
        } else if (statement instanceof Ast.Switch switchStatement) {
            executeSwitch(switchStatement);
        } else if (statement instanceof Ast.Try tryStatement) {
            executeTry(tryStatement);
        } else if (statement instanceof Ast.Throw throwStatement) {
            executeThrow(throwStatement);
        } else if (statement instanceof Ast.Return returnStatement) {
            executeReturn(returnStatement);
        } else if (statement instanceof Ast.Break breakStatement) {
            step(breakStatement.line(), () -> {
                trace.note(TraceAction.STATEMENT, "break out of the loop");
                throw BreakSignal.INSTANCE;
            });
        } else if (statement instanceof Ast.Continue continueStatement) {
            step(continueStatement.line(), () -> {
                trace.note(TraceAction.STATEMENT, "continue to the next iteration");
                throw ContinueSignal.INSTANCE;
            });
        } else if (!(statement instanceof Ast.Empty)) {
            throw new InterpreterException(
                    "Unsupported statement " + statement.getClass().getSimpleName(),
                    statement.line());
        }
    }

    private void executeBlock(Ast.Block block) {
        Frame frame = openScope();
        try {
            for (Ast.Stmt statement : block.statements()) {
                execute(statement);
            }
        } finally {
            closeScope(frame);
        }
    }

    private void executeIf(Ast.If branch) {
        if (condition(branch.line(), branch.condition(), "if")) {
            execute(branch.thenBranch());
        } else if (branch.elseBranch() != null) {
            execute(branch.elseBranch());
        }
    }

    private void executeWhile(Ast.While loop) {
        while (condition(loop.line(), loop.condition(), "while")) {
            if (runLoopBody(loop.body())) {
                break;
            }
        }
    }

    private void executeDoWhile(Ast.DoWhile loop) {
        do {
            if (runLoopBody(loop.body())) {
                break;
            }
        } while (condition(loop.line(), loop.condition(), "while"));
    }

    private void executeFor(Ast.For loop) {
        Frame frame = openScope();
        try {
            for (Ast.Stmt initializer : loop.initializers()) {
                execute(initializer);
            }
            while (true) {
                budget.step();
                if (loop.condition() != null && !condition(loop.line(), loop.condition(), "for")) {
                    break;
                }
                if (runLoopBody(loop.body())) {
                    break;
                }
                for (Ast.Expr update : loop.updates()) {
                    step(loop.line(), () -> evaluate(update));
                }
            }
        } finally {
            closeScope(frame);
        }
    }

    /**
     * A for-each loop over an array or a collection.
     *
     * <p>The elements are snapshotted before the loop starts. That is not just convenience: it
     * means mutating the collection inside the loop cannot corrupt the iteration, and it matches
     * what a learner expects far better than Java's {@code ConcurrentModificationException}
     * would.
     */
    private void executeForEach(Ast.ForEach loop) {
        Object iterable = evaluate(loop.iterable());
        if (iterable == null) {
            throw new InterpreterException(
                    "NullPointerException: cannot iterate over null", loop.line());
        }

        List<Object> elements = new ArrayList<>();
        String sourceName;
        if (iterable instanceof ArrayValue array) {
            elements.addAll(java.util.Arrays.asList(array.raw()));
            sourceName = array.name();
        } else if (iterable instanceof ListValue || iterable instanceof SetValue
                || iterable instanceof MapValue) {
            for (Object element : CollectionSupport.iterate(iterable, loop.line())) {
                elements.add(element);
            }
            sourceName = containerName(iterable);
        } else {
            throw new InterpreterException(
                    "A for-each loop needs an array or a collection but got "
                            + Values.typeName(iterable),
                    loop.line());
        }

        Frame frame = openScope();
        try {
            frame.scope.declare(loop.name(), loop.type(), Values.defaultValue(loop.type()));
            // Resolved once: the loop variable's slot cannot move, and looking it up per
            // iteration would resolve against whatever frame happens to be active.
            Environment.Slot slot = frame.scope.lookup(loop.name());

            for (int position = 0; position < elements.size(); position++) {
                final int index = position;
                final Object element = elements.get(position);
                step(loop.line(), () -> {
                    trace.countArrayRead();
                    if (sourceName != null) {
                        trace.touch(Touch.read(sourceName, index, jsonSafe(element)));
                    }
                    slot.set(Values.coerce(loop.type(), element, "to '" + loop.name() + "'",
                            loop.line()));
                    markChanged(loop.name());
                    trace.note(TraceAction.ASSIGN, "%s = %s (element %d of %s)".formatted(
                            loop.name(), Values.format(element), index, sourceName));
                });
                if (runLoopBody(loop.body())) {
                    break;
                }
            }
        } finally {
            closeScope(frame);
        }
    }

    /** Runs one loop body iteration. Returns true when the loop should stop. */
    private boolean runLoopBody(Ast.Stmt body) {
        try {
            execute(body);
            return false;
        } catch (BreakSignal ignored) {
            return true;
        } catch (ContinueSignal ignored) {
            return false;
        }
    }

    /**
     * A switch, in both forms.
     *
     * <p>Fall-through is real: the colon form keeps running into the following cases until a
     * {@code break} or the end of the block, which is why {@code case 1: case 2:} works and
     * why forgetting a {@code break} behaves the way it does in Java. The arrow form stops
     * after its own body.
     */
    private void executeSwitch(Ast.Switch node) {
        Object selector = valueInStep(node.line(), node.selector(), "switch on ");

        int start = -1;
        int fallbackIndex = -1;
        for (int i = 0; i < node.cases().size() && start < 0; i++) {
            Ast.SwitchCase branch = node.cases().get(i);
            if (branch.isDefault()) {
                fallbackIndex = i;
                continue;
            }
            for (Ast.Expr label : branch.labels()) {
                if (Values.areEqual(selector, evaluate(label))) {
                    start = i;
                    break;
                }
            }
        }
        if (start < 0) {
            start = fallbackIndex;
        }
        if (start < 0) {
            return;
        }

        try {
            for (int i = start; i < node.cases().size(); i++) {
                Ast.SwitchCase branch = node.cases().get(i);
                for (Ast.Stmt statement : branch.statements()) {
                    execute(statement);
                }
                if (branch.arrowForm()) {
                    break;
                }
            }
        } catch (BreakSignal ignored) {
            // 'break' binds to the nearest enclosing switch or loop -- here, this switch.
            // 'continue' is deliberately not caught: it belongs to the enclosing loop.
        }
    }

    /**
     * {@code try}/{@code catch}/{@code finally}.
     *
     * <p>Both sources of failure are handled: an explicit {@code throw} arrives as a
     * {@link ThrowSignal}, and an interpreter-raised Java exception (a bad index, a division by
     * zero) arrives as an {@link InterpreterException}. The second is only catchable when it
     * really is a Java exception -- an "unsupported syntax" refusal is not, because letting
     * {@code catch (Exception e)} swallow it would hide a clear message behind silently wrong
     * behaviour.
     *
     * <p>{@code finally} runs on every path, including a {@code return} or {@code break} leaving
     * the block and an exception nobody caught.
     */
    private void executeTry(Ast.Try node) {
        try {
            try {
                executeBlock(node.body());
            } catch (ThrowSignal signal) {
                if (!runMatchingCatch(node, signal.thrown)) {
                    throw signal;
                }
            } catch (InterpreterException failure) {
                if (!failure.isCatchable()) {
                    throw failure;
                }
                ThrownValue thrown =
                        new ThrownValue(failure.exceptionType(), failure.detail());
                if (!runMatchingCatch(node, thrown)) {
                    throw failure;
                }
            }
        } finally {
            if (node.finallyBlock() != null) {
                executeBlock(node.finallyBlock());
            }
        }
    }

    /** Runs the first catch clause whose type matches. Returns false when none does. */
    private boolean runMatchingCatch(Ast.Try node, ThrownValue thrown) {
        for (Ast.CatchClause clause : node.catches()) {
            for (String declared : clause.types()) {
                String caught = Values.simpleTypeName(Values.rawType(declared));
                if (!thrown.matches(caught)) {
                    continue;
                }
                Frame frame = openScope();
                try {
                    frame.scope.declare(clause.name(), caught, thrown);
                    trace.synthetic(TraceAction.STATEMENT, clause.line(),
                            "Caught " + thrown.render(), snapshotVariables(),
                            snapshotVisualizations(), snapshotCallStack(),
                            Math.max(0, frames.size() - 1));
                    executeBlock(clause.body());
                } finally {
                    closeScope(frame);
                }
                return true;
            }
        }
        return false;
    }

    private void executeThrow(Ast.Throw node) {
        step(node.line(), () -> {
            Object value = evaluate(node.value());
            if (!(value instanceof ThrownValue thrown)) {
                throw new InterpreterException(
                        "Only exceptions can be thrown, but got " + Values.typeName(value),
                        node.line());
            }
            trace.note(TraceAction.ERROR, "Threw " + thrown.render());
            throw new ThrowSignal(thrown);
        });
    }

    /** Evaluates an expression as one trace step and returns its value. */
    private Object valueInStep(int line, Ast.Expr expression, String label) {
        Object[] result = new Object[1];
        step(line, () -> {
            result[0] = evaluate(expression);
            trace.note(TraceAction.CONDITION, label + Values.format(result[0]));
        });
        return result[0];
    }

    private void executeReturn(Ast.Return returnStatement) {
        step(returnStatement.line(), () -> {
            Object value = returnStatement.value() == null ? null
                    : evaluate(returnStatement.value());
            trace.note(TraceAction.STATEMENT, value == null ? "return"
                    : "return " + Values.format(value));
            throw new ReturnSignal(value);
        });
    }

    private boolean condition(int line, Ast.Expr expression, String keyword) {
        boolean[] result = new boolean[1];
        step(line, () -> {
            result[0] = Values.truth(evaluate(expression), line);
            trace.note(TraceAction.CONDITION, keyword + " condition is " + result[0]);
        });
        return result[0];
    }

    private void declareLocals(Ast.VarDecl declaration) {
        for (Ast.Declarator declarator : declaration.declarators()) {
            String type = declaredTypeOf(declaration, declarator);
            Object value = declarator.initializer() == null
                    ? Values.defaultValue(type)
                    : Values.coerce(type, evaluate(declarator.initializer()),
                            "to '" + declarator.name() + "'", declaration.line());
            nameContainer(value, declarator.name());
            frames.peek().scope.declare(declarator.name(), type, value);
            markChanged(declarator.name());
            trace.note(TraceAction.DECLARE,
                    "%s %s = %s".formatted(type, declarator.name(), Values.format(value)));
        }
    }

    // ------------------------------------------------------------------ stepping

    /**
     * Executes one statement as a single trace step: charges the budget, opens a staging event,
     * runs the body, then commits the state the statement produced.
     *
     * <p>Break, continue and return unwind through here, and their events are committed on the
     * way out so the trace still shows the statement that caused the jump.
     */
    private void step(int line, Runnable body) {
        budget.step();
        Frame frame = frames.peek();
        frame.line = line;
        trace.begin(line);
        changedNames.push(new HashSet<>(2));
        try {
            body.run();
            commitStep();
        } catch (Signal signal) {
            commitStep();
            throw signal;
        } catch (RuntimeException failure) {
            changedNames.poll();
            throw failure;
        }
    }

    private void commitStep() {
        trace.commit(snapshotVariables(), snapshotVisualizations(), snapshotCallStack(),
                Math.max(0, frames.size() - 1));
        changedNames.poll();
    }

    private void markChanged(String name) {
        Set<String> changed = changedNames.peek();
        if (changed != null) {
            changed.add(name);
        }
    }

    /**
     * Opens a block scope and returns the frame it belongs to.
     *
     * <p>The returned frame must be the one passed to {@link #closeScope}. Resolving the frame
     * again at close time via {@code frames.peek()} looks equivalent but is not: when an
     * exception unwinds out of a nested method call, {@code frames.peek()} is the <em>callee's</em>
     * frame, so every enclosing block's cleanup would pop scopes off the wrong frame and walk its
     * chain past the root. Pairing open and close on the same frame keeps each frame's scope
     * stack balanced no matter how execution leaves the block.
     */
    private Frame openScope() {
        Frame frame = frames.peek();
        frame.scope = new Environment(frame.scope);
        return frame;
    }

    private static void closeScope(Frame frame) {
        frame.scope = frame.scope.enclosing();
    }

    // ------------------------------------------------------------------ expressions

    private Object evaluate(Ast.Expr expression) {
        if (expression instanceof Ast.Literal literal) {
            return literal.value();
        }
        if (expression instanceof Ast.Name name) {
            return readName(name);
        }
        if (expression instanceof Ast.This thisExpression) {
            return self(thisExpression.line());
        }
        if (expression instanceof Ast.Index index) {
            return readElement(resolveElement(index), index.line());
        }
        if (expression instanceof Ast.Field field) {
            return readField(field);
        }
        if (expression instanceof Ast.Call call) {
            return call(call);
        }
        if (expression instanceof Ast.Unary unary) {
            return unary(unary);
        }
        if (expression instanceof Ast.Binary binary) {
            return binary(binary);
        }
        if (expression instanceof Ast.Logical logical) {
            return logical(logical);
        }
        if (expression instanceof Ast.Assign assign) {
            return assign(assign);
        }
        if (expression instanceof Ast.IncDec incDec) {
            return incDec(incDec);
        }
        if (expression instanceof Ast.Ternary ternary) {
            return Values.truth(evaluate(ternary.condition()), ternary.line())
                    ? evaluate(ternary.thenBranch())
                    : evaluate(ternary.elseBranch());
        }
        if (expression instanceof Ast.Cast cast) {
            return Values.cast(cast.type(), evaluate(cast.expression()), cast.line());
        }
        if (expression instanceof Ast.InstanceOf test) {
            return isInstanceOf(evaluate(test.expression()),
                    Values.simpleTypeName(Values.rawType(test.type())));
        }
        if (expression instanceof Ast.NewArray newArray) {
            return newArray(newArray);
        }
        if (expression instanceof Ast.NewObject newObject) {
            return newObject(newObject);
        }
        if (expression instanceof Ast.ArrayLiteral literal) {
            return arrayLiteral(literal);
        }
        throw new InterpreterException(
                "Unsupported expression " + expression.getClass().getSimpleName(),
                expression.line());
    }

    /**
     * {@code instanceof}. Null is never an instance of anything, as in Java.
     *
     * <p>Without inheritance there is no hierarchy to walk, so this is an exact-type test plus
     * the collection interfaces ({@code List}, {@code Map}, ...) that
     * {@link CollectionSupport#accepts} already knows how to check.
     */
    private boolean isInstanceOf(Object value, String rawType) {
        if (value == null) {
            return false;
        }
        if ("Object".equals(rawType)) {
            return true;
        }
        if (CollectionSupport.accepts(rawType, value)) {
            return true;
        }
        if (value instanceof ObjectValue object) {
            return object.className().equals(rawType);
        }
        if (value instanceof ThrownValue thrown) {
            return thrown.matches(rawType);
        }
        return switch (rawType) {
            case "String" -> value instanceof String;
            case "int", "Integer" -> value instanceof Integer;
            case "long", "Long" -> value instanceof Long;
            case "double", "Double" -> value instanceof Double;
            case "boolean", "Boolean" -> value instanceof Boolean;
            case "char", "Character" -> value instanceof Character;
            default -> false;
        };
    }

    private ObjectValue self(int line) {
        Frame frame = frames.peek();
        if (frame == null || frame.self == null) {
            throw new InterpreterException(
                    "'this' is only available inside an instance method or constructor", line);
        }
        return frame.self;
    }

    private Object readName(Ast.Name name) {
        Environment.Slot slot = resolve(name.name());
        if (slot == null) {
            throw new InterpreterException("Cannot find variable '" + name.name() + "'",
                    name.line());
        }
        return slot.value();
    }

    /**
     * Locals, then the current instance's fields, then static fields -- the same order Java
     * resolves an unqualified name in, which is what makes {@code data = 3} inside a method mean
     * {@code this.data = 3}.
     */
    private Environment.Slot resolve(String name) {
        Frame frame = frames.peek();
        if (frame != null) {
            Environment.Slot local = frame.scope.lookup(name);
            if (local != null) {
                return local;
            }
            if (frame.self != null) {
                Environment.Slot field = frame.self.field(name);
                if (field != null) {
                    return field;
                }
            }
        }
        return globals.lookup(name);
    }

    private Environment.Slot requireSlot(Ast.Name name) {
        Environment.Slot slot = resolve(name.name());
        if (slot == null) {
            throw new InterpreterException("Cannot find variable '" + name.name() + "'",
                    name.line());
        }
        return slot;
    }

    /** A resolved array cell: the array object plus a bounds-checked index. */
    private record ElementRef(ArrayValue array, int index) {
    }

    private ElementRef resolveElement(Ast.Index node) {
        Object target = evaluate(node.target());
        if (target == null) {
            throw new InterpreterException(
                    "NullPointerException: the array is null and cannot be indexed", node.line());
        }
        if (!(target instanceof ArrayValue array)) {
            throw new InterpreterException(
                    "Cannot index a value of type " + Values.typeName(target), node.line());
        }
        int index = Values.toIndex(evaluate(node.index()), node.line());
        if (index < 0 || index >= array.length()) {
            throw new InterpreterException(
                    "ArrayIndexOutOfBoundsException: Index " + index
                            + " out of bounds for length " + array.length(),
                    node.line());
        }
        return new ElementRef(array, index);
    }

    private Object readElement(ElementRef ref, int line) {
        Object value = ref.array().get(ref.index());
        trace.countArrayRead();
        trace.touch(Touch.read(ref.array().name(), ref.index(), jsonSafe(value)));
        trace.note(TraceAction.ARRAY_READ, "Read %s[%d] = %s".formatted(ref.array().name(),
                ref.index(), Values.format(value)));
        return value;
    }

    private Object readField(Ast.Field field) {
        String namespace = staticNamespace(field.target());
        if (namespace != null) {
            if ("System".equals(namespace) && "out".equals(field.name())) {
                throw new InterpreterException(
                        "System.out can only be used as System.out.println(...) or "
                                + "System.out.print(...)",
                        field.line());
            }
            return Builtins.staticField(namespace, field.name(), field.line());
        }

        Object target = evaluate(field.target());
        if (target == null) {
            throw new InterpreterException(
                    "NullPointerException: cannot read '" + field.name() + "' because "
                            + render(field.target()) + " is null",
                    field.line());
        }
        if (target instanceof ArrayValue array && "length".equals(field.name())) {
            return array.length();
        }
        if (target instanceof ObjectValue instance) {
            Environment.Slot slot = instance.field(field.name());
            if (slot == null) {
                throw new InterpreterException(
                        "Class " + instance.className() + " has no field '" + field.name() + "'",
                        field.line());
            }
            Object value = slot.value();
            trace.countFieldRead();
            trace.touch(Touch.read(instance.reference(), -1, jsonSafe(value)));
            trace.note(TraceAction.FIELD_READ, "Read %s.%s = %s".formatted(
                    render(field.target()), field.name(), Values.format(value)));
            return value;
        }
        if (target instanceof String && "length".equals(field.name())) {
            throw new InterpreterException(
                    "String length is a method: use s.length() instead of s.length", field.line());
        }
        throw new InterpreterException(
                "Type " + Values.typeName(target) + " has no field '" + field.name() + "'",
                field.line());
    }

    /**
     * Recognises {@code Math}, {@code Arrays}, {@code Integer}, ... as static namespaces rather
     * than variables -- but only when no variable of that name is in scope, so a local called
     * {@code max} still wins over anything built in.
     */
    private String staticNamespace(Ast.Expr expression) {
        if (expression instanceof Ast.Name name
                && Builtins.NAMESPACES.contains(name.name())
                && resolve(name.name()) == null) {
            return name.name();
        }
        return null;
    }

    /** Renders an expression back to something source-like, for trace messages. */
    private static String render(Ast.Expr expression) {
        if (expression instanceof Ast.Name name) {
            return name.name();
        }
        if (expression instanceof Ast.This) {
            return "this";
        }
        if (expression instanceof Ast.Field field) {
            return render(field.target()) + "." + field.name();
        }
        if (expression instanceof Ast.Index index) {
            return render(index.target()) + "[..]";
        }
        if (expression instanceof Ast.Call call) {
            return render(call.callee()) + "(..)";
        }
        return "value";
    }

    private Object unary(Ast.Unary unary) {
        Object value = evaluate(unary.operand());
        return switch (unary.operator()) {
            case "-" -> Values.negate(value, unary.line());
            case "+" -> value;
            case "~" -> Values.complement(value, unary.line());
            case "!" -> !Values.truth(value, unary.line());
            default -> throw new InterpreterException(
                    "Unsupported unary operator '" + unary.operator() + "'", unary.line());
        };
    }

    private Object logical(Ast.Logical logical) {
        boolean left = Values.truth(evaluate(logical.left()), logical.line());
        if ("&&".equals(logical.operator())) {
            return left && Values.truth(evaluate(logical.right()), logical.line());
        }
        return left || Values.truth(evaluate(logical.right()), logical.line());
    }

    /** An evaluated operand plus, when it came from an array cell or object, where from. */
    private record Operand(Object value, String target, int index, String label) {
    }

    private Object binary(Ast.Binary binary) {
        String operator = binary.operator();
        if (!isComparison(operator)) {
            return Values.binary(operator, evaluate(binary.left()), evaluate(binary.right()),
                    binary.line());
        }

        Operand left = operand(binary.left());
        Operand right = operand(binary.right());
        Object result = Values.binary(operator, left.value(), right.value(), binary.line());
        String message = "%s %s %s -> %s".formatted(left.label(), operator, right.label(),
                Values.format(result));

        // A comparison counts as an *element* comparison -- the number a complexity analysis
        // talks about -- only when at least one side came out of a data structure. Loop bounds
        // like "i < arr.length" are conditions, not comparisons of the data.
        boolean structural = left.target() != null || right.target() != null;
        if (structural) {
            trace.countComparison();
            if (left.target() != null) {
                trace.touch(Touch.compare(left.target(), left.index(), jsonSafe(left.value())));
            }
            if (right.target() != null) {
                trace.touch(Touch.compare(right.target(), right.index(), jsonSafe(right.value())));
            }
            trace.note(TraceAction.COMPARE, message);
        } else {
            trace.note(TraceAction.CONDITION, message);
        }
        return result;
    }

    private static boolean isComparison(String operator) {
        return switch (operator) {
            case "<", ">", "<=", ">=", "==", "!=" -> true;
            default -> false;
        };
    }

    private Operand operand(Ast.Expr expression) {
        if (expression instanceof Ast.Index index) {
            ElementRef ref = resolveElement(index);
            Object value = readElement(ref, index.line());
            String label = "%s[%d]=%s".formatted(ref.array().name(), ref.index(),
                    Values.format(value));
            return new Operand(value, ref.array().name(), ref.index(), label);
        }
        Object value = evaluate(expression);
        // Comparing two references -- 'slow == fast' -- is the operation cycle detection is
        // built on, so both nodes are highlighted just like two array cells would be.
        if (value instanceof ObjectValue instance
                && (expression instanceof Ast.Name || expression instanceof Ast.Field
                        || expression instanceof Ast.This)) {
            return new Operand(value, instance.reference(), -1,
                    render(expression) + "=" + instance.reference());
        }
        return new Operand(value, null, -1, describe(expression, value));
    }

    /** Renders an operand as "name=value" where a name exists, so messages read like the code. */
    private static String describe(Ast.Expr expression, Object value) {
        if (expression instanceof Ast.Name name) {
            return name.name() + "=" + Values.format(value);
        }
        if (expression instanceof Ast.Field field) {
            return render(field) + "=" + Values.format(value);
        }
        return Values.format(value);
    }

    private Object assign(Ast.Assign node) {
        String operator = node.operator();
        boolean compound = !"=".equals(operator);
        String binaryOperator = compound
                ? operator.substring(0, operator.length() - 1)
                : null;

        if (node.target() instanceof Ast.Index indexNode) {
            ElementRef ref = resolveElement(indexNode);
            Object previous = ref.array().get(ref.index());
            Object value;
            if (compound) {
                Object current = readElement(ref, node.line());
                value = Values.binary(binaryOperator, current, evaluate(node.value()),
                        node.line());
                value = compoundConvert(ref.array().elementType(), value, node.line());
            } else {
                value = Values.coerce(ref.array().elementType(), evaluate(node.value()),
                        "to " + ref.array().name() + "[" + ref.index() + "]", node.line());
            }
            ref.array().set(ref.index(), value);
            trace.countArrayWrite();
            trace.touch(Touch.write(ref.array().name(), ref.index(), jsonSafe(value)));
            trace.note(TraceAction.ARRAY_WRITE, "%s[%d] = %s (was %s)".formatted(
                    ref.array().name(), ref.index(), Values.format(value),
                    Values.format(previous)));
            return value;
        }

        if (node.target() instanceof Ast.Field fieldNode) {
            return assignField(fieldNode, node, compound, binaryOperator);
        }

        Ast.Name name = (Ast.Name) node.target();
        Environment.Slot slot = requireSlot(name);
        Object previous = slot.value();
        Object value;
        if (compound) {
            value = Values.binary(binaryOperator, previous, evaluate(node.value()), node.line());
            value = compoundConvert(slot.declaredType(), value, node.line());
        } else {
            value = Values.coerce(slot.declaredType(), evaluate(node.value()),
                    "to '" + name.name() + "'", node.line());
        }
        slot.set(value);
        nameContainer(value, name.name());
        markChanged(name.name());
        trace.note(TraceAction.ASSIGN, "%s = %s (was %s)".formatted(name.name(),
                Values.format(value), Values.format(previous)));
        return value;
    }

    private Object assignField(Ast.Field fieldNode, Ast.Assign node, boolean compound,
            String binaryOperator) {
        Object owner = evaluate(fieldNode.target());
        if (owner == null) {
            throw new InterpreterException(
                    "NullPointerException: cannot assign '" + fieldNode.name() + "' because "
                            + render(fieldNode.target()) + " is null",
                    node.line());
        }
        if (owner instanceof ArrayValue) {
            throw new InterpreterException("An array's length cannot be assigned", node.line());
        }
        if (!(owner instanceof ObjectValue instance)) {
            throw new InterpreterException(
                    "Type " + Values.typeName(owner) + " has no assignable field '"
                            + fieldNode.name() + "'",
                    node.line());
        }
        Environment.Slot slot = instance.field(fieldNode.name());
        if (slot == null) {
            throw new InterpreterException(
                    "Class " + instance.className() + " has no field '" + fieldNode.name() + "'",
                    node.line());
        }

        Object previous = slot.value();
        Object value;
        if (compound) {
            value = Values.binary(binaryOperator, previous, evaluate(node.value()), node.line());
            value = compoundConvert(slot.declaredType(), value, node.line());
        } else {
            value = Values.coerce(slot.declaredType(), evaluate(node.value()),
                    "to field '" + fieldNode.name() + "'", node.line());
        }
        slot.set(value);
        trace.countFieldWrite();
        trace.touch(Touch.write(instance.reference(), -1, jsonSafe(value)));
        trace.note(TraceAction.FIELD_WRITE, "%s.%s = %s (was %s)".formatted(
                render(fieldNode.target()), fieldNode.name(), Values.format(value),
                Values.format(previous)));
        return value;
    }

    /**
     * Compound assignment carries an implicit narrowing cast in Java -- {@code int x; x += 1.5;}
     * compiles and truncates. Plain assignment does not, which is why the two paths differ.
     */
    private Object compoundConvert(String declaredType, Object value, int line) {
        String raw = Values.rawType(declaredType);
        if (Values.isArrayType(declaredType) || classes.containsKey(raw)
                || CollectionSupport.isKnownType(raw)) {
            return Values.coerce(declaredType, value, "in a compound assignment", line);
        }
        return Values.cast(declaredType, value, line);
    }

    private Object incDec(Ast.IncDec node) {
        String operator = "++".equals(node.operator()) ? "+" : "-";
        if (node.target() instanceof Ast.Index indexNode) {
            ElementRef ref = resolveElement(indexNode);
            Object previous = readElement(ref, node.line());
            Object updated = compoundConvert(ref.array().elementType(),
                    Values.binary(operator, previous, 1, node.line()), node.line());
            ref.array().set(ref.index(), updated);
            trace.countArrayWrite();
            trace.touch(Touch.write(ref.array().name(), ref.index(), jsonSafe(updated)));
            trace.note(TraceAction.ARRAY_WRITE, "%s[%d] = %s (was %s)".formatted(
                    ref.array().name(), ref.index(), Values.format(updated),
                    Values.format(previous)));
            return node.prefix() ? updated : previous;
        }

        if (node.target() instanceof Ast.Field fieldNode) {
            Object owner = evaluate(fieldNode.target());
            if (!(owner instanceof ObjectValue instance)) {
                throw new InterpreterException(
                        "'" + node.operator() + "' needs a numeric field", node.line());
            }
            Environment.Slot slot = instance.field(fieldNode.name());
            if (slot == null) {
                throw new InterpreterException("Class " + instance.className()
                        + " has no field '" + fieldNode.name() + "'", node.line());
            }
            Object previous = slot.value();
            Object updated = compoundConvert(slot.declaredType(),
                    Values.binary(operator, previous, 1, node.line()), node.line());
            slot.set(updated);
            trace.countFieldWrite();
            trace.touch(Touch.write(instance.reference(), -1, jsonSafe(updated)));
            trace.note(TraceAction.FIELD_WRITE, "%s.%s = %s (was %s)".formatted(
                    render(fieldNode.target()), fieldNode.name(), Values.format(updated),
                    Values.format(previous)));
            return node.prefix() ? updated : previous;
        }

        Ast.Name name = (Ast.Name) node.target();
        Environment.Slot slot = requireSlot(name);
        Object previous = slot.value();
        Object updated = compoundConvert(slot.declaredType(),
                Values.binary(operator, previous, 1, node.line()), node.line());
        slot.set(updated);
        markChanged(name.name());
        trace.note(TraceAction.ASSIGN, "%s = %s (was %s)".formatted(name.name(),
                Values.format(updated), Values.format(previous)));
        return node.prefix() ? updated : previous;
    }

    private Object newArray(Ast.NewArray node) {
        List<Integer> sizes = new ArrayList<>(node.dimensions().size());
        for (Ast.Expr dimension : node.dimensions()) {
            int size = Values.toIndex(evaluate(dimension), node.line());
            budget.checkArrayLength(size, node.line());
            sizes.add(size);
        }
        return allocate(node.elementType(), sizes, 0);
    }

    private ArrayValue allocate(String baseType, List<Integer> sizes, int depth) {
        int size = sizes.get(depth);
        boolean leaf = depth == sizes.size() - 1;
        String elementType = leaf ? baseType
                : baseType + "[]".repeat(sizes.size() - depth - 1);
        if (leaf) {
            return ArrayValue.ofSize(elementType, size, Values.defaultValue(baseType));
        }
        Object[] rows = new Object[size];
        for (int i = 0; i < size; i++) {
            rows[i] = allocate(baseType, sizes, depth + 1);
        }
        return new ArrayValue(elementType, rows);
    }

    private Object arrayLiteral(Ast.ArrayLiteral literal) {
        String elementType = literal.elementType() == null ? "int" : literal.elementType();
        budget.checkArrayLength(literal.elements().size(), literal.line());
        Object[] values = new Object[literal.elements().size()];
        for (int i = 0; i < values.length; i++) {
            Object value = evaluate(literal.elements().get(i));
            values[i] = Values.coerce(elementType, value, "in the array initialiser",
                    literal.line());
        }
        return new ArrayValue(elementType, values);
    }

    /**
     * Creates an instance: fields get their defaults, then their initialisers with {@code this}
     * bound, then the matching constructor runs.
     *
     * <p>Defaults are installed before any initialiser so a field initialiser reading another
     * field sees {@code 0}/{@code null} rather than a missing slot -- the same two-phase order
     * the JVM uses.
     */
    private Object newObject(Ast.NewObject node) {
        String rawType = Values.rawType(node.className());
        // Trailing simple name, so 'new java.util.ArrayList<>()' works like 'new ArrayList<>()'.
        String simpleName = rawType.contains(".")
                ? rawType.substring(rawType.lastIndexOf('.') + 1)
                : rawType;

        List<Object> arguments = new ArrayList<>(node.arguments().size());
        for (Ast.Expr argument : node.arguments()) {
            arguments.add(evaluate(argument));
        }

        if (CollectionSupport.isConstructible(simpleName)) {
            Object created = CollectionSupport.construct(simpleName, arguments, node.line());
            trace.countObjectCreated();
            trace.note(TraceAction.ALLOCATE,
                    "Created " + simpleName + " " + Values.format(created));
            return created;
        }

        if (EXCEPTION_TYPES.contains(simpleName)) {
            String message = arguments.isEmpty() ? null : Values.format(arguments.get(0));
            return new ThrownValue(simpleName, message);
        }

        Ast.ClassDecl declaration = classes.get(simpleName);
        if (declaration == null) {
            throw new InterpreterException(
                    "Cannot find class '" + node.className() + "'. Declare it in this submission,"
                            + " or use one of the supported library types (ArrayList, LinkedList,"
                            + " HashMap, HashSet, ArrayDeque, PriorityQueue, Stack, TreeMap,"
                            + " TreeSet, StringBuilder).",
                    node.line());
        }

        budget.checkCallDepth(frames.size());
        ObjectValue instance = new ObjectValue(simpleName, nextObjectId++);
        trace.countObjectCreated();

        for (Ast.VarDecl field : declaration.instanceFields()) {
            for (Ast.Declarator declarator : field.declarators()) {
                String type = declaredTypeOf(field, declarator);
                instance.declareField(declarator.name(), type, Values.defaultValue(type));
            }
        }

        frames.push(new Frame("<init>", new Environment(null), node.line(), instance));
        try {
            for (Ast.VarDecl field : declaration.instanceFields()) {
                for (Ast.Declarator declarator : field.declarators()) {
                    if (declarator.initializer() == null) {
                        continue;
                    }
                    String type = declaredTypeOf(field, declarator);
                    Object value = Values.coerce(type, evaluate(declarator.initializer()),
                            "to field '" + declarator.name() + "'", field.line());
                    instance.field(declarator.name()).set(value);
                }
            }
        } finally {
            frames.pop();
        }

        Ast.MethodDecl constructor = findMethod(declaration.constructors(), simpleName,
                arguments.size());
        if (constructor != null) {
            invoke(constructor, arguments, node.line(), instance);
        } else if (!arguments.isEmpty()) {
            throw new InterpreterException(
                    "Class " + simpleName + " has no constructor taking "
                            + arguments.size() + " argument(s)",
                    node.line());
        }

        trace.note(TraceAction.ALLOCATE, "Created " + instance.render());
        return instance;
    }

    // ------------------------------------------------------------------ calls

    private Object call(Ast.Call node) {
        if (node.callee() instanceof Ast.Name name) {
            List<Object> arguments = evaluateArguments(node);

            Ast.MethodDecl method = findMethod(methods, name.name(), arguments.size());
            if (method != null) {
                return invoke(method, arguments, node.line(), null);
            }

            // An unqualified call inside an instance method binds to 'this'.
            Frame frame = frames.peek();
            if (frame != null && frame.self != null) {
                Ast.ClassDecl declaration = classes.get(frame.self.className());
                Ast.MethodDecl own = declaration == null ? null
                        : findMethod(declaration.methods(), name.name(), arguments.size());
                if (own != null) {
                    return invoke(own, arguments, node.line(),
                            own.isStatic() ? null : frame.self);
                }
            }
            throw unknownMethod(name.name(), arguments.size(), methods, node.line());
        }

        if (node.callee() instanceof Ast.Field field) {
            // System.out.println(...) / System.out.print(...)
            if (field.target() instanceof Ast.Field inner
                    && "out".equals(inner.name())
                    && "System".equals(staticNamespace(inner.target()))) {
                return print(field.name(), evaluateArguments(node), node.line());
            }
            String namespace = staticNamespace(field.target());
            if (namespace != null) {
                return Builtins.callStatic(namespace, field.name(), evaluateArguments(node),
                        node.line());
            }

            // Solution.helper(...) -- a static method reached through its class name. Only when
            // no variable shadows the name, so a local called `solution` still wins.
            if (field.target() instanceof Ast.Name typeName
                    && classes.containsKey(typeName.name())
                    && resolve(typeName.name()) == null) {
                List<Object> arguments = evaluateArguments(node);
                Ast.ClassDecl declaration = classes.get(typeName.name());
                Ast.MethodDecl method =
                        findMethod(declaration.methods(), field.name(), arguments.size());
                if (method == null) {
                    throw unknownMethod(typeName.name() + "." + field.name(), arguments.size(),
                            declaration.methods(), node.line());
                }
                if (!method.isStatic()) {
                    throw new InterpreterException(
                            "'" + field.name() + "()' is an instance method, so it needs an "
                                    + "object: new " + typeName.name() + "()." + field.name()
                                    + "(...)",
                            node.line());
                }
                return invoke(method, arguments, node.line(), null);
            }

            Object receiver = evaluate(field.target());
            List<Object> arguments = evaluateArguments(node);

            if (receiver instanceof ListValue || receiver instanceof MapValue
                    || receiver instanceof SetValue || receiver instanceof BuilderValue
                    || receiver instanceof EntryValue || receiver instanceof ThrownValue) {
                CollectionSupport.Outcome outcome =
                        CollectionSupport.invoke(receiver, field.name(), arguments, node.line());
                recordContainerAccess(receiver, field.name(), arguments, outcome);
                return outcome.value();
            }

            if (receiver instanceof ObjectValue instance) {
                Ast.ClassDecl declaration = classes.get(instance.className());
                Ast.MethodDecl method = declaration == null ? null
                        : findMethod(declaration.methods(), field.name(), arguments.size());
                if (method != null) {
                    return invoke(method, arguments, node.line(),
                            method.isStatic() ? null : instance);
                }
                throw unknownMethod(instance.className() + "." + field.name(), arguments.size(),
                        declaration == null ? List.of() : declaration.methods(), node.line());
            }
            if (receiver == null) {
                throw new InterpreterException(
                        "NullPointerException: cannot call '" + field.name() + "()' because "
                                + render(field.target()) + " is null",
                        node.line());
            }
            return Builtins.callInstance(receiver, field.name(), arguments, node.line());
        }

        throw new InterpreterException("Unsupported call target", node.line());
    }

    /**
     * Records what a collection call did, so it lands in the trace like an array access.
     *
     * <p>{@link CollectionSupport} reports the element position it touched, which is what lets a
     * {@code list.get(3)} or a {@code map.put(k, v)} highlight the same way {@code arr[3]} does.
     * A structural call with no single position ({@code clear}, {@code addAll}) still counts as a
     * write but highlights nothing.
     */
    private void recordContainerAccess(Object receiver, String method, List<Object> arguments,
            CollectionSupport.Outcome outcome) {
        String target = containerName(receiver);
        if (target == null) {
            // A StringBuilder or an entry: real, but not something the canvas draws.
            return;
        }
        String call = "%s.%s(%s)".formatted(target, method, formatArguments(arguments));

        if (outcome.touched() && outcome.index() >= 0) {
            if (outcome.write()) {
                trace.countArrayWrite();
                trace.touch(Touch.write(target, outcome.index(), jsonSafe(outcome.value())));
                trace.note(TraceAction.ARRAY_WRITE, call);
            } else {
                trace.countArrayRead();
                trace.touch(Touch.read(target, outcome.index(), jsonSafe(outcome.value())));
                trace.note(TraceAction.ARRAY_READ,
                        call + " = " + Values.format(outcome.value()));
            }
        } else if (outcome.write()) {
            trace.countArrayWrite();
            trace.note(TraceAction.ARRAY_WRITE, call);
        }
    }

    private String formatArguments(List<Object> arguments) {
        List<String> parts = new ArrayList<>(arguments.size());
        for (Object argument : arguments) {
            parts.add(Values.shallow(argument));
        }
        return String.join(", ", parts);
    }

    /** The display name of a drawable container, or null when it is not drawn. */
    private static String containerName(Object value) {
        if (value instanceof ListValue list) {
            return list.name();
        }
        if (value instanceof MapValue map) {
            return map.name();
        }
        if (value instanceof SetValue set) {
            return set.name();
        }
        return null;
    }

    /** Binds a container to the first variable it is stored in, so the picture has a label. */
    private static void nameContainer(Object value, String name) {
        if (value instanceof ArrayValue array) {
            array.nameIfUnnamed(name);
        } else if (value instanceof ListValue list) {
            list.nameIfUnnamed(name);
        } else if (value instanceof MapValue map) {
            map.nameIfUnnamed(name);
        } else if (value instanceof SetValue set) {
            set.nameIfUnnamed(name);
        }
    }

    /** Overload resolution is by name and argument count. */
    private static Ast.MethodDecl findMethod(List<Ast.MethodDecl> candidates, String name,
            int arity) {
        for (Ast.MethodDecl candidate : candidates) {
            if (candidate.name().equals(name) && candidate.arity() == arity) {
                return candidate;
            }
        }
        return null;
    }

    private static Ast.MethodDecl findAnyByName(List<Ast.MethodDecl> candidates, String name) {
        for (Ast.MethodDecl candidate : candidates) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    /** Reports a missing method, naming the arities that do exist when the name matches. */
    private static InterpreterException unknownMethod(String name, int arity,
            List<Ast.MethodDecl> candidates, int line) {
        String simpleName = name.contains(".") ? name.substring(name.indexOf('.') + 1) : name;
        List<String> arities = new ArrayList<>();
        for (Ast.MethodDecl candidate : candidates) {
            if (candidate.name().equals(simpleName)) {
                arities.add(String.valueOf(candidate.arity()));
            }
        }
        if (!arities.isEmpty()) {
            return new InterpreterException(
                    "Method '" + name + "' cannot take " + arity + " argument(s). It is declared "
                            + "taking " + String.join(" or ", arities) + ".",
                    line);
        }
        return new InterpreterException(
                "Cannot find method '" + name + "'. Only methods declared in this submission and "
                        + "the supported built-ins can be called.",
                line);
    }

    private List<Object> evaluateArguments(Ast.Call node) {
        List<Object> arguments = new ArrayList<>(node.arguments().size());
        for (Ast.Expr argument : node.arguments()) {
            arguments.add(evaluate(argument));
        }
        return arguments;
    }

    private Object print(String method, List<Object> arguments, int line) {
        switch (method) {
            case "println" -> {
                trace.print((arguments.isEmpty() ? "" : Values.format(arguments.get(0))) + "\n");
                return null;
            }
            case "print" -> {
                trace.print(arguments.isEmpty() ? "" : Values.format(arguments.get(0)));
                return null;
            }
            case "printf", "format" -> {
                if (arguments.isEmpty()) {
                    throw new InterpreterException("printf needs a format string", line);
                }
                trace.print(Builtins.format(Values.format(arguments.get(0)),
                        arguments.subList(1, arguments.size()), line));
                return null;
            }
            case "flush" -> {
                return null;
            }
            default -> throw new InterpreterException(
                    "System.out has no '" + method + "()'. Supported: println, print, printf.",
                    line);
        }
    }

    private Object invoke(Ast.MethodDecl method, List<Object> arguments, int callLine,
            ObjectValue receiver) {
        budget.checkCallDepth(frames.size());
        trace.countCall();

        Environment root = new Environment(null);
        List<String> labels = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); i++) {
            Ast.Param parameter = method.parameters().get(i);
            Object value = Values.coerce(parameter.type(), arguments.get(i),
                    "to parameter '" + parameter.name() + "'", callLine);
            // Keep the caller's name so a helper's picture is the caller's picture.
            nameContainer(value, parameter.name());
            root.declare(parameter.name(), parameter.type(), value);
            labels.add(parameter.name() + "=" + Values.format(value));
        }

        // A constructor frame is labelled "new Node" rather than "Node", so the call stack
        // cannot be misread as the object reference "Node@1".
        String frameLabel = method.isConstructor() ? "new " + method.name() : method.name();
        frames.push(new Frame(frameLabel, root, method.line(), receiver));
        trace.synthetic(TraceAction.CALL, callLine,
                "%s %s(%s)".formatted(method.isConstructor() ? "Constructing" : "Called",
                        method.name(), String.join(", ", labels)),
                snapshotVariables(), snapshotVisualizations(), snapshotCallStack(),
                frames.size() - 1);

        Object result = null;
        try {
            execute(method.body());
        } catch (ReturnSignal signal) {
            result = signal.value;
        } catch (BreakSignal | ContinueSignal signal) {
            throw new InterpreterException(
                    "'break' or 'continue' used outside of a loop in '" + method.name() + "'",
                    frames.peek().line);
        }
        // Popped only on normal completion: if the callee threw, leaving its frame on the stack
        // is what lets the error step report the line and locals where it actually failed.
        frames.pop();

        if (!method.isConstructor() && !"void".equals(method.returnType())) {
            result = Values.coerce(method.returnType(), result,
                    "as the return value of '" + method.name() + "'", callLine);
        }
        trace.synthetic(TraceAction.RETURN, callLine, returnMessage(method, result),
                snapshotVariables(), snapshotVisualizations(), snapshotCallStack(),
                Math.max(0, frames.size() - 1));
        return result;
    }

    private static String returnMessage(Ast.MethodDecl method, Object result) {
        if (method.isConstructor()) {
            return "Finished constructing " + method.name();
        }
        if ("void".equals(method.returnType())) {
            return "Returned from " + method.name() + "()";
        }
        return "%s() returned %s".formatted(method.name(), Values.format(result));
    }

    // ------------------------------------------------------------------ snapshots

    /**
     * Converts a runtime value into something safe to put in a trace event.
     *
     * <p>This is load bearing. An {@link ObjectValue} handed to Jackson would serialise its
     * internal field slots, and a linked list with a cycle -- exactly what people build to test
     * cycle detection -- would recurse until the request died. Every value crossing into the
     * trace goes through here and comes out as a primitive or a string.
     */
    private static Object jsonSafe(Object value) {
        if (value instanceof ObjectValue object) {
            return object.reference();
        }
        if (value instanceof ArrayValue || value instanceof ListValue
                || value instanceof MapValue || value instanceof SetValue
                || value instanceof EntryValue || value instanceof BuilderValue
                || value instanceof ThrownValue) {
            // Values.format is depth limited, so a self-referencing container is safe here.
            return Values.format(value);
        }
        return value;
    }

    private Map<String, Environment.Slot> visibleSlots() {
        Map<String, Environment.Slot> visible = new LinkedHashMap<>();
        globals.collectVisible(visible);
        Frame frame = frames.peek();
        if (frame != null) {
            if (frame.self != null) {
                // Instance fields are visible unqualified, and are shown as 'this.x' so they
                // are not mistaken for locals.
                for (Map.Entry<String, Environment.Slot> field : frame.self.fields().entrySet()) {
                    visible.put("this." + field.getKey(), field.getValue());
                }
            }
            frame.scope.collectVisible(visible);
        }
        return visible;
    }

    private List<VariableValue> snapshotVariables() {
        Set<String> changed = changedNames.isEmpty() ? Set.of() : changedNames.peek();
        List<VariableValue> variables = new ArrayList<>();
        for (Map.Entry<String, Environment.Slot> entry : visibleSlots().entrySet()) {
            Object value = entry.getValue().value();
            if (value instanceof ArrayValue || CollectionSupport.isContainer(value)) {
                // Drawn as visualizations, so listing them here too would be noise. A
                // StringBuilder is not drawn, so it deliberately stays in this list.
                continue;
            }
            variables.add(new VariableValue(entry.getKey(), entry.getValue().declaredType(),
                    jsonSafe(value), changed.contains(entry.getKey())));
        }
        return variables;
    }

    private List<VisualizationState> snapshotVisualizations() {
        Map<String, Environment.Slot> visible = visibleSlots();
        List<VisualizationState> visualizations = new ArrayList<>();

        collectArrays(visible, visualizations);
        collectCollections(visible, visualizations);
        collectObjectGraphs(visible, visualizations);

        return visualizations.isEmpty() ? null : visualizations;
    }

    /**
     * Draws lists, stacks, queues, sets and maps.
     *
     * <p>The end markers matter as much as the contents: a stack drawn without a {@code top}
     * marker and a queue drawn without {@code front}/{@code back} are the same picture, even
     * though {@code pop} takes from opposite ends. So each shape gets the pointers that make its
     * behaviour visible.
     */
    private void collectCollections(Map<String, Environment.Slot> visible,
            List<VisualizationState> into) {
        Map<Object, String> seen = new IdentityHashMap<>();
        for (Map.Entry<String, Environment.Slot> entry : visible.entrySet()) {
            Object value = entry.getValue().value();
            if (!CollectionSupport.isContainer(value) || seen.containsKey(value)) {
                continue;
            }
            seen.put(value, entry.getKey());
            String name = containerName(value);

            if (value instanceof MapValue map) {
                List<VisualizationState.MapEntry> entries = new ArrayList<>(map.size());
                int index = 0;
                for (Map.Entry<Object, Object> pair : map.entries().entrySet()) {
                    entries.add(new VisualizationState.MapEntry(index++,
                            jsonSafe(pair.getKey()), jsonSafe(pair.getValue()),
                            ElementState.DEFAULT));
                }
                into.add(VisualizationState.map(name, map.kind().displayName(), entries));
                continue;
            }

            List<Object> ordered = CollectionSupport.orderedElements(value);
            List<ArrayElement> elements = new ArrayList<>(ordered.size());
            for (int i = 0; i < ordered.size(); i++) {
                elements.add(ArrayElement.plain(i, jsonSafe(ordered.get(i))));
            }

            if (value instanceof SetValue set) {
                into.add(VisualizationState.sequence(VisualizationType.SET, name,
                        set.kind().displayName(), elements, null));
                continue;
            }

            ListValue list = (ListValue) value;
            VisualizationType shape = shapeOf(list.kind(),
                    Values.simpleTypeName(Values.rawType(entry.getValue().declaredType())));
            into.add(VisualizationState.sequence(shape, name, list.kind().displayName(),
                    elements, endMarkers(list, shape, visible)));
        }
    }

    /**
     * Picks a layout from the declared type first, then the implementation class.
     *
     * <p>The declared type expresses intent better: {@code Queue<Integer> q = new LinkedList<>()}
     * is the standard way to write a BFS frontier, and drawing it as an indexed list -- with no
     * front or back marker -- would hide the very thing that makes it a queue. A
     * {@code LinkedList} declared as a {@code List} still draws as an indexed row, which is also
     * what its use implies.
     */
    private static VisualizationType shapeOf(ListValue.Kind kind, String declaredType) {
        if (declaredType != null) {
            switch (declaredType) {
                case "Queue", "Deque", "ArrayDeque", "PriorityQueue" -> {
                    return VisualizationType.QUEUE;
                }
                case "Stack" -> {
                    return VisualizationType.STACK;
                }
                default -> {
                    // Fall through to the implementation class.
                }
            }
        }
        return switch (kind) {
            case STACK -> VisualizationType.STACK;
            case ARRAY_DEQUE, PRIORITY_QUEUE -> VisualizationType.QUEUE;
            case ARRAY_LIST, LINKED_LIST -> VisualizationType.ARRAY;
        };
    }

    /** The markers that make a stack's or a queue's active end obvious. */
    private List<Pointer> endMarkers(ListValue list, VisualizationType shape,
            Map<String, Environment.Slot> visible) {
        List<Pointer> pointers = new ArrayList<>();
        int size = list.size();

        if (size > 0) {
            if (shape == VisualizationType.STACK) {
                pointers.add(Pointer.atIndex("top", size - 1));
            } else if (shape == VisualizationType.QUEUE) {
                if (list.kind() == ListValue.Kind.PRIORITY_QUEUE) {
                    // A heap has no back worth naming; what matters is what comes out next.
                    pointers.add(Pointer.atIndex("next", 0));
                } else {
                    pointers.add(Pointer.atIndex("front", 0));
                    if (size > 1) {
                        pointers.add(Pointer.atIndex("back", size - 1));
                    }
                }
            }
        }

        // An indexable list also gets the index arrows an array would get.
        if (shape == VisualizationType.ARRAY) {
            for (Map.Entry<String, Environment.Slot> entry : visible.entrySet()) {
                if (!POINTER_NAMES.contains(entry.getKey())) {
                    continue;
                }
                if (entry.getValue().value() instanceof Integer index
                        && index >= 0 && index < size) {
                    pointers.add(Pointer.atIndex(entry.getKey(), index));
                }
            }
        }
        return pointers.isEmpty() ? null : pointers;
    }

    private void collectArrays(Map<String, Environment.Slot> visible,
            List<VisualizationState> into) {
        // Dedupe by identity: an array passed to a helper is visible under two names but is one
        // object, and drawing it twice would suggest there are two arrays.
        Map<ArrayValue, String> seen = new IdentityHashMap<>();
        List<ArrayValue> ordered = new ArrayList<>();
        for (Map.Entry<String, Environment.Slot> entry : visible.entrySet()) {
            if (entry.getValue().value() instanceof ArrayValue array
                    && array != syntheticArgs
                    && !seen.containsKey(array)) {
                seen.put(array, entry.getKey());
                ordered.add(array);
            }
        }
        for (ArrayValue array : ordered) {
            String name = array.hasName() ? array.name() : seen.get(array);
            into.add(array.isNested() ? matrixOf(name, array) : arrayOf(name, array, visible));
        }
    }

    private VisualizationState arrayOf(String name, ArrayValue array,
            Map<String, Environment.Slot> visible) {
        List<ArrayElement> elements = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            elements.add(ArrayElement.plain(i, jsonSafe(array.get(i))));
        }
        List<Pointer> pointers = new ArrayList<>();
        for (Map.Entry<String, Environment.Slot> entry : visible.entrySet()) {
            if (!POINTER_NAMES.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue().value() instanceof Integer index
                    && index >= 0 && index < array.length()) {
                pointers.add(Pointer.atIndex(entry.getKey(), index));
            }
        }
        return VisualizationState.array(name, array.elementType(), elements,
                pointers.isEmpty() ? null : pointers);
    }

    private VisualizationState matrixOf(String name, ArrayValue array) {
        List<List<ArrayElement>> rows = new ArrayList<>(array.length());
        for (int r = 0; r < array.length(); r++) {
            List<ArrayElement> row = new ArrayList<>();
            if (array.get(r) instanceof ArrayValue rowArray) {
                for (int c = 0; c < rowArray.length(); c++) {
                    row.add(ArrayElement.plain(c, jsonSafe(rowArray.get(c))));
                }
            }
            rows.add(row);
        }
        // elementType of an int[][] is "int[]", so the cell type is one dimension down.
        return VisualizationState.matrix(name, Values.elementTypeOf(array.elementType()), rows);
    }

    // ------------------------------------------------------------------ object graphs

    /**
     * Draws every reference-linked structure in scope.
     *
     * <p>Objects are gathered by walking references from the visible variables, then split into
     * connected components over the <em>undirected</em> reference graph. Components rather than
     * per-variable trees, because a list reachable from both {@code head} and {@code slow} is
     * one picture with two pointers on it -- not two pictures.
     */
    private void collectObjectGraphs(Map<String, Environment.Slot> visible,
            List<VisualizationState> into) {
        List<ObjectValue> reachable = reachableObjects(visible);
        if (reachable.isEmpty()) {
            return;
        }

        Map<ObjectValue, Set<ObjectValue>> adjacency = undirectedAdjacency(reachable);
        Set<ObjectValue> assigned = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ObjectValue root : reachable) {
            if (assigned.contains(root)) {
                continue;
            }
            List<ObjectValue> component = new ArrayList<>();
            Deque<ObjectValue> queue = new ArrayDeque<>();
            queue.add(root);
            assigned.add(root);
            while (!queue.isEmpty()) {
                ObjectValue current = queue.poll();
                component.add(current);
                for (ObjectValue neighbour : adjacency.getOrDefault(current, Set.of())) {
                    if (assigned.add(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
            into.add(buildGraph(component, visible));
        }
    }

    /** Breadth-first walk from every visible reference, including those inside arrays. */
    private List<ObjectValue> reachableObjects(Map<String, Environment.Slot> visible) {
        List<ObjectValue> ordered = new ArrayList<>();
        Set<ObjectValue> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<ObjectValue> queue = new ArrayDeque<>();

        for (Environment.Slot slot : visible.values()) {
            enqueueRoots(slot.value(), seen, queue, ordered);
        }
        while (!queue.isEmpty() && ordered.size() < MAX_GRAPH_NODES) {
            ObjectValue current = queue.poll();
            for (Environment.Slot field : current.fields().values()) {
                if (field.value() instanceof ObjectValue referenced && seen.add(referenced)) {
                    ordered.add(referenced);
                    queue.add(referenced);
                }
            }
        }
        return ordered;
    }

    private void enqueueRoots(Object value, Set<ObjectValue> seen, Deque<ObjectValue> queue,
            List<ObjectValue> ordered) {
        if (value instanceof ObjectValue object) {
            if (seen.add(object)) {
                ordered.add(object);
                queue.add(object);
            }
        } else if (value instanceof ArrayValue array && !array.isNested()) {
            for (Object element : array.raw()) {
                if (element instanceof ObjectValue object && seen.add(object)) {
                    ordered.add(object);
                    queue.add(object);
                }
            }
        }
    }

    private Map<ObjectValue, Set<ObjectValue>> undirectedAdjacency(List<ObjectValue> nodes) {
        Set<ObjectValue> members = Collections.newSetFromMap(new IdentityHashMap<>());
        members.addAll(nodes);

        Map<ObjectValue, Set<ObjectValue>> adjacency = new IdentityHashMap<>();
        for (ObjectValue node : nodes) {
            for (Environment.Slot field : node.fields().values()) {
                if (field.value() instanceof ObjectValue target && members.contains(target)) {
                    adjacency.computeIfAbsent(node,
                            key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(target);
                    adjacency.computeIfAbsent(target,
                            key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(node);
                }
            }
        }
        return adjacency;
    }

    private VisualizationState buildGraph(List<ObjectValue> component,
            Map<String, Environment.Slot> visible) {
        List<VisualizationState.GraphNode> nodes = new ArrayList<>(component.size());
        List<VisualizationState.GraphEdge> edges = new ArrayList<>();
        Set<String> referenceFieldNames = new HashSet<>();

        for (ObjectValue object : component) {
            List<VariableValue> fields = new ArrayList<>();
            Object payload = null;
            boolean payloadFound = false;

            for (Map.Entry<String, Environment.Slot> entry : object.fields().entrySet()) {
                Environment.Slot slot = entry.getValue();
                fields.add(VariableValue.of(entry.getKey(), slot.declaredType(),
                        jsonSafe(slot.value())));

                if (isReferenceField(slot)) {
                    referenceFieldNames.add(entry.getKey());
                    Object target = slot.value();
                    edges.add(new VisualizationState.GraphEdge(object.reference(),
                            target instanceof ObjectValue referenced ? referenced.reference()
                                    : null,
                            entry.getKey()));
                } else if (!payloadFound) {
                    // The first non-reference field is the value a learner reads off the node.
                    payload = jsonSafe(slot.value());
                    payloadFound = true;
                }
            }

            nodes.add(new VisualizationState.GraphNode(object.reference(), object.className(),
                    payload, fields, ElementState.DEFAULT));
        }

        Set<ObjectValue> members = Collections.newSetFromMap(new IdentityHashMap<>());
        members.addAll(component);
        List<Pointer> pointers = new ArrayList<>();
        String name = null;
        for (Map.Entry<String, Environment.Slot> entry : visible.entrySet()) {
            if (entry.getValue().value() instanceof ObjectValue object
                    && members.contains(object)) {
                pointers.add(Pointer.atNode(entry.getKey(), object.reference()));
                if (name == null) {
                    name = entry.getKey();
                }
            }
        }

        return VisualizationState.objectGraph(shapeOf(referenceFieldNames),
                name != null ? name : component.get(0).className(),
                component.get(0).className(), nodes, edges,
                pointers.isEmpty() ? null : pointers);
    }

    /** A field is a reference when its declared type is one of this submission's classes. */
    private boolean isReferenceField(Environment.Slot slot) {
        return classes.containsKey(Values.rawType(slot.declaredType()));
    }

    /**
     * Chooses a layout from the names of the reference fields, not from the runtime shape.
     *
     * <p>Naming is a far better signal than structure here: a tree whose right subtree happens
     * to be empty is structurally a list, and re-classifying it mid-run would make the picture
     * jump around as nodes are inserted. {@code next}/{@code prev} means a list,
     * {@code left}/{@code right} means a tree, and anything else is drawn as a general graph.
     */
    private static VisualizationType shapeOf(Set<String> referenceFieldNames) {
        if (referenceFieldNames.isEmpty() || LIST_FIELDS.containsAll(referenceFieldNames)) {
            return VisualizationType.LINKED_LIST;
        }
        if (TREE_FIELDS.containsAll(referenceFieldNames)) {
            return VisualizationType.TREE;
        }
        return VisualizationType.GRAPH;
    }

    /**
     * The call stack, outermost frame first. Omitted entirely while only the entry frame is
     * active: it would just duplicate the variables panel on every one of thousands of steps.
     */
    private List<StackFrameState> snapshotCallStack() {
        if (frames.size() <= 1) {
            return null;
        }
        List<Frame> ordered = new ArrayList<>(frames);
        Collections.reverse(ordered);
        List<StackFrameState> stack = new ArrayList<>(ordered.size());
        for (Frame frame : ordered) {
            Map<String, Environment.Slot> visible = new LinkedHashMap<>();
            frame.scope.collectVisible(visible);
            List<VariableValue> variables = new ArrayList<>(visible.size());
            if (frame.self != null) {
                variables.add(VariableValue.of("this", frame.self.className(),
                        frame.self.reference()));
            }
            for (Map.Entry<String, Environment.Slot> entry : visible.entrySet()) {
                Environment.Slot slot = entry.getValue();
                variables.add(VariableValue.of(entry.getKey(), slot.declaredType(),
                        jsonSafe(slot.value())));
            }
            stack.add(new StackFrameState(frame.method, frame.line, variables));
        }
        return stack;
    }
}
