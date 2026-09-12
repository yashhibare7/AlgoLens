# The supported Java subset

This is the contract. `Parser` and `Builtins` enforce it, and everything listed as unsupported is
rejected with a message naming what is missing rather than a generic error.

## Accepted submission shapes

All of these work. Paste whichever you have.

```java
// A class with a nested helper class -- the usual single-file linked list.
import java.util.*;

public class Main {
    static class Node {
        int data;
        Node next;
        Node(int data) {
            this.data = data;
            this.next = null;
        }
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        // ...
    }
}
```

```java
// Several top-level classes, main in any of them.
class Node {
    int data;
    Node next;
    Node(int data) { this.data = data; }
}

public class Main {
    public static void main(String[] args) { ... }
}
```

```java
// 1. Bare statements. No class, no main.
int[] arr = {5, 2, 8, 1};
for (int i = 0; i < arr.length; i++) {
    System.out.println(arr[i]);
}
```

```java
// 2. A full class with a main method.
public class Main {
    static int factorial(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        System.out.println(factorial(5));
    }
}
```

```java
// 3. Statements plus helper methods, no class wrapper.
static int square(int n) {
    return n * n;
}

int result = square(7);
```

`package` and `import` lines are accepted and ignored. Nested classes are treated as static and
flattened into a single namespace -- the subset has no inner-class scoping, and `static class
Node` inside `Main` is how most single-file linked lists are written. A class with no `main` is
accepted if it has exactly one no-argument method, which is then the entry point.

---

## Types

| Supported | Notes |
|---|---|
| `int`, `long`, `double`, `boolean`, `char`, `String` | `short`, `byte` and `float` parse and behave as `int`/`double` |
| Arrays of all of the above | `int[]`, `String[]`, ... |
| 2D arrays | `int[][] grid = new int[3][3];` — drawn as a grid, cells not highlighted yet |
| `final` on locals | Accepted; immutability is not enforced |

Declaration forms: `int[] a = {1, 2, 3};`, `int[] a = new int[5];`,
`int[] a = new int[]{1, 2, 3};`, `int a[] = {1, 2};`, and multiple declarators
(`int i = 0, j = 1;`).

### Numeric semantics match `javac`

This is not incidental. If `(low + high) / 2` silently became floating point, or `int` stopped
wrapping, a student debugging a real overflow in their binary search would find the visualizer
disagreeing with the compiler — and the tool would be teaching the wrong thing.

```java
int half = 7 / 2;                    // 3, not 3.5
int wrapped = 2147483647 + 1;        // -2147483648
double exact = 7 / 2.0;              // 3.5
int truncated = (int) 3.9;           // 3
int bad = 3.9;                       // rejected: add an (int) cast
long widened = 42;                   // fine, widening is implicit
int narrowed = 42L;                  // rejected: possible lossy conversion
int compound = 0; compound += 1.5;   // allowed — compound assignment casts implicitly, as in Java
```

`if (x)` where `x` is an `int` is rejected with an explanation that Java has no truthy values.

---

## Control flow

| Supported | Example |
|---|---|
| `if` / `else` / `else if` | |
| `for` | `for (int i = 0; i < n; i++)`, empty sections, comma-separated init and update |
| enhanced `for` | `for (int value : arr)` |
| `while`, `do-while` | |
| `break`, `continue` | Unlabelled only |
| `return` | With or without a value |
| Blocks, and single statements without braces | |

| `switch` | Both forms: `case 1:` with real fall-through, and `case 1 ->` without |
| `try` / `catch` / `finally` | Multi-catch (`catch (A \| B e)`) included |
| `throw` | `throw new IllegalArgumentException("...")` |

Not supported: labelled `break`/`continue`, `switch` as an *expression*, try-with-resources.

### switch

Fall-through is real, because getting it wrong is a bug people need to *see*:

```java
switch (day) {
    case 1:
    case 2:
        kind = "early";     // case 1 falls into case 2
        break;
    case 3:
        kind = "mid";       // no break: falls into default
    default:
        kind = kind + "!";
}
```

Switches on `int`, `char` and `String`. The arrow form never falls through.

### Exceptions

Two kinds are catchable, and one deliberately is not:

```java
try {
    int x = arr[99];                       // interpreter-raised -> catchable
    throw new IllegalStateException("no"); // user-thrown       -> catchable
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println(e.getMessage());
} catch (Exception e) {                    // catches anything above
    System.out.println("other");
} finally {
    System.out.println("always runs");
}
```

**An "unsupported feature" refusal is not catchable.** If `catch (Exception e)` could swallow
"lambdas are not in the supported subset yet", a clear message would turn into silently wrong
behaviour. So those propagate and fail the run.

Since there is no inheritance, custom exception classes (`class MyError extends Exception`) do
not work. The built-in names — `RuntimeException`, `IllegalArgumentException`,
`IllegalStateException`, `NullPointerException`, `ArithmeticException`,
`NumberFormatException`, `IndexOutOfBoundsException`, `NoSuchElementException` and friends —
all do.

## Objects and classes

Enough of an object model to build every reference-linked data structure:

```java
static class Node {
    int data;
    Node next;

    Node(int data) {          // constructor
        this.data = data;     // 'this'
        this.next = null;
    }

    int doubled() {           // instance method
        return data * 2;      // implicit 'this'
    }
}
```

| Feature | Notes |
|---|---|
| Class declarations | Top level, and nested (`static class Node`). Nesting flattens to one namespace |
| Instance fields | With or without initialisers; initialisers run before the constructor |
| Static fields | Shared, initialised once |
| Constructors | Overloaded on argument count |
| Instance methods | `this` is bound, and an unqualified name resolves to a field |
| Static methods | Callable without a receiver |
| Field access and assignment | Chained: `head.next.next = new Node(3)` |
| `new Node(...)` | And `new Node[10]`, an array of references defaulting to `null` |
| Reference equality | `slow == fast` compares identity, which is what cycle detection needs |
| `null` | Assignable, comparable, and dereferencing it reports *which* expression was null |

**Overloads resolve on argument count only.** `f(int)` and `f(int, int)` are fine; `f(int)` and
`f(double)` are rejected at parse time with an explanation rather than silently mis-dispatched.
That covers essentially all DSA code without implementing Java's full overload resolution, which
is genuinely intricate.

Arrays and objects are passed by reference, exactly as in Java — which is why
`swap(arr, i, j)` mutates the caller's array, and why the visualization keeps showing the
caller's structure while execution is inside a helper.

Not supported: inheritance, `extends`, interfaces, abstract classes, `instanceof`, varargs,
inner (non-static) classes.

### How a structure gets drawn

The layout is chosen from the **names** of the reference fields, not from the runtime shape:

| Reference fields | Drawn as |
|---|---|
| `next`, `prev`, `previous`, or any single field | Linked list — a row of nodes with arrows; a cycle is arced underneath |
| `left`, `right`, `parent` | Tree — depth for rows, in-order position for columns |
| anything else | Object graph — breadth-first layers |

Naming beats structure here: a tree whose right subtree happens to be empty is *structurally* a
list, and reclassifying it mid-run would make the picture jump around as nodes are inserted.

## Methods

Static methods with any number of parameters, including recursion.

## Operators

Everything you need, at correct Java precedence:

```
arithmetic     +  -  *  /  %          (integer division and overflow are faithful)
comparison     <  >  <=  >=  ==  !=
logical        &&  ||  !              (short circuiting)
bitwise        &  |  ^  ~
shift          <<  >>  >>>
assignment     =  +=  -=  *=  /=  %=  &=  |=  ^=  <<=  >>=
increment      ++  --                 (prefix and postfix, on variables and array elements)
ternary        cond ? a : b
cast           (int) (double) (char) (long)
string concat  "value: " + x
array          arr[i]   arr.length
```

### Two deliberate deviations from real Java, both about printing

`"Sorted: " + arr` prints `[1, 2, 5, 8]`, where real Java prints `[I@1b6d3586`. The hash form
teaches nothing in a visualizer; the contents are what a learner expects.

Printing an object gives `Node@1{data=1, next=Node@2}` — structural, and **only one level
deep**. Your `toString()` is *not* called implicitly. Two reasons: a cyclic list (exactly what
people build to test cycle detection) would render forever, and building a trace message must
never execute interpreted code, because that would run side effects and burn the step budget
just to label a step. Call `node.toString()` explicitly and it works normally.

These are the only two places the subset knowingly differs in behaviour.

---

## Standard library

Only pure, deterministic functions. A trace must replay identically when reopened from history
days later, so anything touching the clock, the filesystem, the network or a random source is
absent by construction.

### Output
`System.out.println(x)`, `System.out.print(x)`

### `Math`
`abs` `max` `min` `pow` `sqrt` `cbrt` `floor` `ceil` `round` `log` `log10` `exp` `signum`
`hypot`, and the constants `PI` and `E`.

`Math.random()` is refused, with a message explaining that a visualised run has to be
reproducible. Use fixed input values.

### Wrappers
- `Integer.parseInt` `valueOf` `toString` `compare` `max` `min` `MAX_VALUE` `MIN_VALUE`
- `Long.parseLong` `valueOf` `toString` `MAX_VALUE` `MIN_VALUE`
- `Double.parseDouble` `valueOf` `toString` `MAX_VALUE` `MIN_VALUE`
- `Character.isDigit` `isLetter` `isLetterOrDigit` `isWhitespace` `isUpperCase` `isLowerCase`
  `toUpperCase` `toLowerCase` `getNumericValue`
- `Boolean.parseBoolean`, `String.valueOf`

### `String` instance methods
`length` `isEmpty` `isBlank` `charAt` `equals` `equalsIgnoreCase` `compareTo` `contains`
`startsWith` `endsWith` `indexOf` `lastIndexOf` `substring` `toUpperCase` `toLowerCase` `trim`
`strip` `replace` `concat` `toCharArray` `split`

`split` treats its argument as a literal separator, not a regex — regex semantics would be a
surprise in a teaching tool, and there is no `Pattern` in the subset.

### `Arrays`
`toString` `deepToString` `fill` `copyOf` `copyOfRange` `equals` `asList` `sort` `binarySearch`

`Arrays.sort` and `Collections.sort` **run** rather than being refused. An earlier version
rejected them on the grounds that sorting in one invisible step defeats a visualizer — that was
the wrong call. Plenty of correct code sorts as a *precondition* (sort, then two-pointer), and
refusing it just made working Java fail. They execute as a single library step, and the trace
labels them as such so it is obvious the intermediate work was not shown. If sorting *is* the
thing you are studying, write the loop and watch it run.

### `Collections`, `List`, `Objects`
- `Collections.sort` `reverse` `swap` `max` `min` `emptyList` `unmodifiableList`
- `List.of(...)` `List.copyOf(c)`
- `Objects.equals` `isNull` `nonNull` `toString`

---

## Collections and generics

Generic syntax parses and is **erased**: `List<Integer>` and `List<String>` are both just
`List` at runtime. Element types are therefore not checked — putting a `String` into a
`List<Integer>` is not caught. Nested arguments work, including the `>>` that closes two levels
at once (`Map<String, List<Integer>>`), and so does the diamond (`new ArrayList<>()`).

| Type | Notes |
|---|---|
| `ArrayList`, `LinkedList` | `get` `set` `add` `add(i,e)` `remove` `indexOf` `contains` `size` `isEmpty` `clear` `addAll` `sort` |
| `Stack` | `push` appends, `pop`/`peek` take from the **end** |
| `ArrayDeque`, `Deque` | `push`/`pop`/`peek` work on the **front**; plus `addFirst` `addLast` `pollFirst` `pollLast` `peekFirst` `peekLast` |
| `Queue` | `offer` to the back, `poll` from the front |
| `PriorityQueue` | Kept sorted; `poll` always returns the smallest. Natural ordering only |
| `HashMap`, `LinkedHashMap`, `TreeMap` | `put` `get` `getOrDefault` `containsKey` `containsValue` `remove` `keySet` `values` `entrySet` `putIfAbsent` `putAll` `size` |
| `HashSet`, `LinkedHashSet`, `TreeSet` | `add` `contains` `remove` `addAll` `size` `isEmpty` |
| `StringBuilder` | `append` (chains) `toString` `length` `charAt` `reverse` `insert` `delete` `deleteCharAt` `setCharAt` `substring` `indexOf` |
| `Map.Entry` | `getKey` `getValue`, from `entrySet()` |

**`push`/`pop` mean different things on a `Stack` and an `ArrayDeque`** — end versus front — and
that difference is implemented, not glossed over. A visualizer that drew those the same way
would be actively misleading.

### Two deliberate choices

**Iteration order is deterministic.** A real `HashMap` has an unspecified order; here it
iterates in insertion order. A stored trace has to replay identically when reopened days later,
and Java promises nothing that this breaks.

**A for-each snapshots the collection first**, so modifying it inside the loop cannot corrupt
the iteration. Java would throw `ConcurrentModificationException`; this simply finishes the loop
over the original elements.

### How collections are drawn

| Declared as | Drawn as |
|---|---|
| `List`, `ArrayList`, `LinkedList` | Indexed row, like an array |
| `Stack` | Row with a `top` marker on the last element |
| `Queue`, `Deque`, `ArrayDeque` | Row with `front` and `back` markers |
| `PriorityQueue` | Row with a `next` marker on the head |
| `Map` family | Key/value rows |
| `Set` family | Row of values, no indices |

The **declared type wins** over the implementation class: `Queue<Integer> q = new LinkedList<>()`
draws as a queue, because that is what the code means.

---

## Not supported yet

| Feature | Status |
|---|---|
| Lambdas and streams (`list.forEach(...)`, `map.computeIfAbsent`, `stream()`) | The largest remaining gap. Methods needing one are refused by name |
| Interfaces, `extends`, abstract classes | Not planned for the MVP. This is why custom exception classes and `Comparable` do not work |
| Custom `Comparator` (`new PriorityQueue<>((a,b) -> ...)`) | Blocked on lambdas; natural ordering works |
| `Iterator` explicitly (`it.hasNext()`) | Use a for-each loop |
| Labelled `break`/`continue`, `switch` expressions, try-with-resources | Planned, self-contained |
| `switch` | Planned |
| Threads, I/O, reflection, `System.exit` | **Never.** There is no grammar for them, which is a large part of why this design is safe |

---

## Limits

Enforced inside the interpreter, so they cannot be talked around. All configurable — see the
README.

| Limit | Default | What happens |
|---|---|---|
| Code length | 20,000 chars | Rejected by validation before parsing |
| Interpreter steps | 200,000 | `LIMIT_EXCEEDED`, with the partial trace |
| Trace events | 20,000 | `LIMIT_EXCEEDED`, `truncated: true` |
| Wall clock | 5 s | `TIMEOUT` |
| Program output | 20,000 chars | `LIMIT_EXCEEDED` |
| Call depth | 200 frames | `LIMIT_EXCEEDED` ("infinite recursion?") |
| Array length | 10,000 | Refused before allocation |

Hitting a limit is a normal outcome, not an error page. You get the steps that ran, the status,
and a message naming the limit — because the steps leading up to a failure are usually the most
useful part of the trace.

---

## Adding to the subset

1. **Lexer** (`Lexer.java`) — a new token, if the syntax needs one.
2. **AST** (`Ast.java`) — a node record. The sealed hierarchies live in one file so `permits`
   clauses can be omitted.
3. **Parser** (`Parser.java`) — parse it into that node.
4. **Interpreter** (`Interpreter.java`) — evaluate it, and emit the right `note`/`touch` calls so
   it shows up in the trace.
5. **Built-ins** (`Builtins.java`) — for library functions. Keep them pure.
6. **Test** — add a case to `InterpreterAlgorithmsTest` (behaviour) or `ParserTest` (syntax), and
   a failure case to `InterpreterFailureTest`.
7. **This document** — it is the contract, so it is part of the change.

If a new feature also needs a new drawable shape, add the `VisualizationType` and a matching
frontend renderer in `components/visualizer/`. Nothing else in the frontend needs to know.
