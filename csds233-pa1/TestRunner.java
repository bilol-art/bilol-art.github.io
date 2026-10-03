import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * CSDS 233 - Programming Assignment 1
 *
 * Headless test harness for {@link PhraseList} and {@link PhraseValidator}.
 * It uses no JUnit and no sound (MidiPlayer is never created).
 *
 * <p>Every test prints the same block: initial list and status, the call
 * being executed, the resulting list and status, a backward link trace, and
 * [SUCCESS] or [FAILED: reason]. {@code checkInvariants()} runs after every
 * single operation, including the ones that build the initial list.</p>
 *
 * <p>Output: console, {@code test_log.txt} (full log) and
 * {@code test_matrix.md} (the test matrix table used in the report).
 * The exit code is 1 if any test fails.</p>
 *
 * <pre>
 *   javac *.java
 *   java TestRunner
 * </pre>
 */
public class TestRunner {

    /** Builds the initial list of a test. */
    private interface Setup {
        PhraseList<String> build();
    }

    /** The operation under test plus its checks; throws TestFailure on a mismatch. */
    private interface Action {
        void run(PhraseList<String> list);
    }

    /** Thrown by the expect* helpers when a check fails. */
    private static final class TestFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TestFailure(String message) {
            super(message);
        }
    }

    /** Node objects and their data captured before an operation, kept in our own list type. */
    private static final class Snapshot {
        final PhraseList<Node<String>> nodes = new PhraseList<Node<String>>();
        final PhraseList<String> data = new PhraseList<String>();
    }

    private static final String C = "C4:1.0";
    private static final String E = "E4:1.0";
    private static final String G = "G4:2.0";
    private static final String TWO = "C4:1.0, E4:1.0";
    private static final String THREE = "C4:1.0, E4:1.0, G4:2.0";
    private static final String FOUR = "C4:1.0, D4:1.0, E4:1.0, F4:1.0";
    private static final String FIVE = "C4:1.0, D4:1.0, E4:1.0, F4:1.0, G4:1.0";
    private static final String SEVEN = "C4:1.0, D4:1.0, E4:1.0, F4:1.0, G4:1.0, A4:1.0, B4:1.0";

    private static final StringBuilder LOG = new StringBuilder();
    private static final StringBuilder SUMMARY = new StringBuilder();
    private static final StringBuilder MATRIX = new StringBuilder();
    private static final StringBuilder MESSAGES = new StringBuilder();
    private static StringBuilder notes = new StringBuilder();
    private static String currentId = "";
    private static int counter = 0;
    private static int testCount = 0;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        log("CSDS 233 PA1 - Interactive Music Sequencer - PhraseList test log");
        log("Java " + System.getProperty("java.version")
                + " | headless (no MidiPlayer) | checkInvariants() after every operation");
        log("");
        MATRIX.append("| Test ID | Category | Initial State | Action | Expected | Actual | Status |\n");
        MATRIX.append("|---|---|---|---|---|---|---|\n");

        runTest("Insert", "INSERT INTO EMPTY LIST SETS CURRENT", empty(),
                "insertLast(\"C4:1.0\")",
                "[C4:1.0] cur=C4:1.0; head == tail == current",
                list -> {
                    list.insertLast(C);
                    step(list, "insertLast");
                    expectState(list, "[C4:1.0] cur=C4:1.0");
                    expectEquals("status", "Size: 1 | Head: C4:1.0 | Tail: C4:1.0 | Current: C4:1.0", list.status());
                    expectTrue(list.nodeAt(0) == list.currentNode(), "current is not the new node");
                    note("head == tail == current (same node)");
                });

        moveTests();
        insertTests();
        cursorTests();
        removalTests();
        boundsTests();
        navigationTests();
        playbackTests();
        validationTests();

        log("=== MESSAGES PRODUCED DURING THE RUN (exceptions and validator errors) ===");
        log(MESSAGES.toString().trim());
        log("");
        log("=== SUMMARY ===");
        log(SUMMARY.toString().trim());
        log("Total: " + testCount + " | Passed: " + passed + " | Failed: " + failed);
        log(failed == 0 ? "ALL TESTS PASSED" : "SOME TESTS FAILED");

        writeFile("test_log.txt", LOG.toString());
        writeFile("test_matrix.md", MATRIX.toString());
        System.out.println();
        System.out.println("Wrote test_log.txt and test_matrix.md");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ==================================================================
    // Move
    // ==================================================================

    private static void moveTests() {
        runTest("Move", "IN-PLACE MOVE (TAIL TO HEAD)", listOf(THREE, 1), "move(2, 0)",
                "[G4:2.0, C4:1.0, E4:1.0] cur=E4:1.0; same nodes, none allocated",
                list -> moveCase(list, 2, 0, "[G4:2.0, C4:1.0, E4:1.0] cur=E4:1.0", "2 0 1"));

        runTest("Move", "IN-PLACE MOVE (HEAD TO TAIL)", listOf(THREE, 1), "move(0, 2)",
                "[E4:1.0, G4:2.0, C4:1.0] cur=E4:1.0; same nodes, none allocated",
                list -> moveCase(list, 0, 2, "[E4:1.0, G4:2.0, C4:1.0] cur=E4:1.0", "1 2 0"));

        runTest("Move", "IN-PLACE MOVE (ADJACENT, FORWARD)", listOf(FOUR, 2), "move(1, 2)",
                "[C4:1.0, E4:1.0, D4:1.0, F4:1.0] cur=E4:1.0",
                list -> moveCase(list, 1, 2, "[C4:1.0, E4:1.0, D4:1.0, F4:1.0] cur=E4:1.0", "0 2 1 3"));

        runTest("Move", "IN-PLACE MOVE (ADJACENT, BACKWARD)", listOf(FOUR, 1), "move(2, 1)",
                "[C4:1.0, E4:1.0, D4:1.0, F4:1.0] cur=D4:1.0",
                list -> moveCase(list, 2, 1, "[C4:1.0, E4:1.0, D4:1.0, F4:1.0] cur=D4:1.0", "0 2 1 3"));

        runTest("Move", "IN-PLACE MOVE (HEAD TO ADJACENT POSITION)", listOf(THREE, 0), "move(0, 1)",
                "[E4:1.0, C4:1.0, G4:2.0] cur=C4:1.0; head updated",
                list -> moveCase(list, 0, 1, "[E4:1.0, C4:1.0, G4:2.0] cur=C4:1.0", "1 0 2"));

        runTest("Move", "IN-PLACE MOVE (TAIL TO ADJACENT POSITION)", listOf(THREE, 2), "move(2, 1)",
                "[C4:1.0, G4:2.0, E4:1.0] cur=G4:2.0; tail updated",
                list -> moveCase(list, 2, 1, "[C4:1.0, G4:2.0, E4:1.0] cur=G4:2.0", "0 2 1"));

        runTest("Move", "IN-PLACE MOVE (MIDDLE, NON-ADJACENT FORWARD)", listOf(FIVE, 1), "move(1, 3)",
                "[C4:1.0, E4:1.0, F4:1.0, D4:1.0, G4:1.0] cur=D4:1.0 (now index 3)",
                list -> {
                    moveCase(list, 1, 3, "[C4:1.0, E4:1.0, F4:1.0, D4:1.0, G4:1.0] cur=D4:1.0", "0 2 3 1 4");
                    expectEquals("getCurrentIndex()", 3, list.getCurrentIndex());
                    note("current followed the moved node to index 3");
                });

        runTest("Move", "IN-PLACE MOVE (MIDDLE, NON-ADJACENT BACKWARD)", listOf(FIVE, 4), "move(3, 1)",
                "[C4:1.0, F4:1.0, D4:1.0, E4:1.0, G4:1.0] cur=G4:1.0",
                list -> moveCase(list, 3, 1, "[C4:1.0, F4:1.0, D4:1.0, E4:1.0, G4:1.0] cur=G4:1.0", "0 3 1 2 4"));

        runTest("Move", "IN-PLACE MOVE ON SIZE 2 (TAIL TO HEAD)", listOf(TWO, 0), "move(1, 0)",
                "[E4:1.0, C4:1.0] cur=C4:1.0; head and tail swapped",
                list -> moveCase(list, 1, 0, "[E4:1.0, C4:1.0] cur=C4:1.0", "1 0"));

        runTest("Move", "IN-PLACE MOVE ON SIZE 2 (HEAD TO TAIL)", listOf(TWO, 1), "move(0, 1)",
                "[E4:1.0, C4:1.0] cur=E4:1.0; head and tail swapped",
                list -> moveCase(list, 0, 1, "[E4:1.0, C4:1.0] cur=E4:1.0", "1 0"));

        runTest("Move", "MOVE WITH SAME SOURCE AND TARGET (NO-OP)", listOf(THREE, 1),
                "move(0, 0); move(1, 1); move(2, 2)",
                "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0; each call returns false",
                list -> {
                    Snapshot snap = snapshot(list);
                    for (int i = 0; i < 3; i++) {
                        int created = Node.instancesCreated;
                        boolean changed = list.move(i, i);
                        int allocated = Node.instancesCreated - created;
                        step(list, "move(" + i + ", " + i + ")");
                        expectTrue(!changed, "move(" + i + ", " + i + ") reported a change");
                        expectEquals("nodes allocated by move(" + i + ", " + i + ")", 0, allocated);
                    }
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    expectNodeOrder(list, snap, "0 1 2");
                    note("all 3 calls returned false; node order 0 1 2 unchanged");
                });

        runTest("Move", "MOVE ON SIZE 1", listOf(C),
                "move(0, 0); move(0, 1); move(1, 0)",
                "[C4:1.0] cur=C4:1.0; move(0,0) is a no-op, the others throw IndexOutOfBoundsException",
                list -> {
                    expectTrue(!list.move(0, 0), "move(0, 0) reported a change");
                    step(list, "move(0, 0)");
                    expectIOOBE(list, "move(0, 1)", () -> list.move(0, 1));
                    expectIOOBE(list, "move(1, 0)", () -> list.move(1, 0));
                    expectState(list, "[C4:1.0] cur=C4:1.0");
                    note("move(0,0) returned false; 2 x IndexOutOfBoundsException, list unchanged");
                });

        runTest("Move", "MOVE ON EMPTY LIST IS REJECTED", empty(),
                "move(0, 0); move(-1, 0)",
                "[] cur=null; IndexOutOfBoundsException, list unchanged",
                list -> {
                    expectIOOBE(list, "move(0, 0)", () -> list.move(0, 0));
                    expectIOOBE(list, "move(-1, 0)", () -> list.move(-1, 0));
                    expectState(list, "[] cur=null");
                    note("2 x IndexOutOfBoundsException, list still empty");
                });

        runTest("Move", "MOVE WITH INVALID INDICES IS REJECTED", listOf(THREE, 1),
                "move(-1, 0); move(3, 0); move(4, 1); move(0, -1); move(0, 3); move(1, 9)",
                "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0; 6 x IndexOutOfBoundsException, same nodes",
                list -> {
                    expectIOOBE(list, "move(-1, 0)", () -> list.move(-1, 0));
                    expectIOOBE(list, "move(3, 0)", () -> list.move(3, 0));
                    expectIOOBE(list, "move(4, 1)", () -> list.move(4, 1));
                    expectIOOBE(list, "move(0, -1)", () -> list.move(0, -1));
                    expectIOOBE(list, "move(0, 3)", () -> list.move(0, 3));
                    expectIOOBE(list, "move(1, 9)", () -> list.move(1, 9));
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    note("6 x IndexOutOfBoundsException (negative, == size, > size for source and target); list and nodes unchanged");
                });

        runTest("Move", "MOVE ROUND TRIPS PRESERVE NODE IDENTITY", listOf(FIVE, 2),
                "move(i, j) then move(j, i) for every i, j in 0..4 (25 round trips)",
                "[C4:1.0, D4:1.0, E4:1.0, F4:1.0, G4:1.0] cur=E4:1.0; original nodes, 0 allocated",
                list -> {
                    Snapshot snap = snapshot(list);
                    Node<String> currentBefore = list.currentNode();
                    int created = Node.instancesCreated;
                    int moves = 0;
                    for (int i = 0; i < 5; i++) {
                        for (int j = 0; j < 5; j++) {
                            list.move(i, j);
                            step(list, "move(" + i + ", " + j + ")");
                            expectTrue(list.nodeAt(j) == snap.nodes.get(i),
                                    "after move(" + i + ", " + j + ") the moved node is not at index " + j);
                            expectTrue(list.currentNode() == currentBefore,
                                    "current changed node after move(" + i + ", " + j + ")");
                            list.move(j, i);
                            step(list, "move(" + j + ", " + i + ")");
                            moves += 2;
                        }
                    }
                    int allocated = Node.instancesCreated - created;
                    expectEquals("nodes allocated by " + moves + " moves", 0, allocated);
                    expectNodeOrder(list, snap, "0 1 2 3 4");
                    expectState(list, "[C4:1.0, D4:1.0, E4:1.0, F4:1.0, G4:1.0] cur=E4:1.0");
                    note(moves + " moves with invariants checked after each; moved node always landed at the target;"
                            + " 0 nodes allocated; final order 0 1 2 3 4");
                });
    }

    // ==================================================================
    // Insert
    // ==================================================================

    private static void insertTests() {
        runTest("Insert", "INSERTFIRST ON EMPTY AND NON-EMPTY LIST", empty(),
                "insertFirst(\"E4:1.0\"); insertFirst(\"C4:1.0\")",
                "[C4:1.0, E4:1.0] cur=E4:1.0",
                list -> {
                    list.insertFirst(E);
                    step(list, "insertFirst(E4:1.0)");
                    expectState(list, "[E4:1.0] cur=E4:1.0");
                    list.insertFirst(C);
                    step(list, "insertFirst(C4:1.0)");
                    expectState(list, "[C4:1.0, E4:1.0] cur=E4:1.0");
                    note("first insert became current; second insert left current alone");
                });

        runTest("Insert", "INSERTAT(0) ON EMPTY LIST SETS CURRENT", empty(),
                "insertAt(0, \"C4:1.0\")",
                "[C4:1.0] cur=C4:1.0",
                list -> {
                    list.insertAt(0, C);
                    step(list, "insertAt(0, C4:1.0)");
                    expectState(list, "[C4:1.0] cur=C4:1.0");
                    expectTrue(list.nodeAt(0) == list.currentNode(), "current is not the new node");
                    note("new node is head, tail and current");
                });

        runTest("Insert", "INSERTAT FRONT, MIDDLE AND END (INDEX == SIZE)", listOf("D4:1.0, F4:1.0", 0),
                "insertAt(0, \"C4:1.0\"); insertAt(2, \"E4:1.0\"); insertAt(4, \"G4:1.0\")",
                "[C4:1.0, D4:1.0, E4:1.0, F4:1.0, G4:1.0] cur=D4:1.0",
                list -> {
                    list.insertAt(0, "C4:1.0");
                    step(list, "insertAt(0)");
                    expectState(list, "[C4:1.0, D4:1.0, F4:1.0] cur=D4:1.0");
                    list.insertAt(2, "E4:1.0");
                    step(list, "insertAt(2)");
                    expectState(list, "[C4:1.0, D4:1.0, E4:1.0, F4:1.0] cur=D4:1.0");
                    list.insertAt(4, "G4:1.0");
                    step(list, "insertAt(4)");
                    expectState(list, "[C4:1.0, D4:1.0, E4:1.0, F4:1.0, G4:1.0] cur=D4:1.0");
                    expectEquals("status", "Size: 5 | Head: C4:1.0 | Tail: G4:1.0 | Current: D4:1.0", list.status());
                    note("head, middle and tail inserts correct; current stayed on D4:1.0");
                });
    }

    // ==================================================================
    // Current-pointer operations
    // ==================================================================

    private static void cursorTests() {
        runTest("Cursor", "INSERT AFTER CURRENT ON EMPTY LIST", empty(),
                "insertAfterCurrent(\"C4:1.0\")",
                "[C4:1.0] cur=C4:1.0 (only node and current)",
                list -> {
                    list.insertAfterCurrent(C);
                    step(list, "insertAfterCurrent");
                    expectState(list, "[C4:1.0] cur=C4:1.0");
                    expectTrue(list.nodeAt(0) == list.currentNode(), "current is not the new node");
                    note("new node is the only node and current");
                });

        runTest("Cursor", "INSERT AFTER CURRENT IN THE MIDDLE", listOf("C4:1.0, G4:2.0", 0),
                "insertAfterCurrent(\"E4:1.0\")",
                "[C4:1.0, E4:1.0, G4:2.0] cur=C4:1.0",
                list -> {
                    list.insertAfterCurrent(E);
                    step(list, "insertAfterCurrent");
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=C4:1.0");
                    note("inserted at index 1; current unchanged");
                });

        runTest("Cursor", "INSERT AFTER CURRENT AT TAIL UPDATES TAIL", listOf(TWO, 1),
                "insertAfterCurrent(\"G4:2.0\")",
                "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0; Tail: G4:2.0",
                list -> {
                    list.insertAfterCurrent(G);
                    step(list, "insertAfterCurrent");
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    expectEquals("status", "Size: 3 | Head: C4:1.0 | Tail: G4:2.0 | Current: E4:1.0", list.status());
                    note("tail is the new node");
                });

        runTest("Cursor", "REPEAT CURRENT ON SIZE 1", listOf(C),
                "repeatCurrent()",
                "[C4:1.0, C4:1.0] cur=C4:1.0 (original head); copy is a new node",
                list -> {
                    expectTrue(list.repeatCurrent(), "repeatCurrent() returned false");
                    step(list, "repeatCurrent");
                    expectState(list, "[C4:1.0, C4:1.0] cur=C4:1.0");
                    expectTrue(list.nodeAt(0) != list.nodeAt(1), "the copy is the same node object");
                    expectEquals("getCurrentIndex()", 0, list.getCurrentIndex());
                    note("returned true; two distinct nodes; current still index 0");
                });

        runTest("Cursor", "REPEAT CURRENT FROM THE MIDDLE APPENDS TO END", listOf(THREE, 1),
                "repeatCurrent()",
                "[C4:1.0, E4:1.0, G4:2.0, E4:1.0] cur=E4:1.0 (index 1)",
                list -> {
                    expectTrue(list.repeatCurrent(), "repeatCurrent() returned false");
                    step(list, "repeatCurrent");
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0, E4:1.0] cur=E4:1.0");
                    expectEquals("getCurrentIndex()", 1, list.getCurrentIndex());
                    note("copy appended as new tail; current still index 1");
                });

        runTest("Cursor", "REPEAT CURRENT ON EMPTY LIST", empty(),
                "repeatCurrent()",
                "[] cur=null; returns false",
                list -> {
                    expectTrue(!list.repeatCurrent(), "repeatCurrent() returned true on an empty list");
                    step(list, "repeatCurrent");
                    expectState(list, "[] cur=null");
                    note("returned false, no exception");
                });
    }

    // ==================================================================
    // Removal
    // ==================================================================

    private static void removalTests() {
        runTest("Remove", "REMOVE CURRENT IN THE MIDDLE (CURRENT -> NEXT)", listOf(THREE, 1),
                "removeCurrent()",
                "[C4:1.0, G4:2.0] cur=G4:2.0; returns E4:1.0",
                list -> removeCurrentCase(list, E, "[C4:1.0, G4:2.0] cur=G4:2.0"));

        runTest("Remove", "REMOVE CURRENT AT TAIL (CURRENT -> PREV)", listOf(THREE, 2),
                "removeCurrent()",
                "[C4:1.0, E4:1.0] cur=E4:1.0; returns G4:2.0",
                list -> removeCurrentCase(list, G, "[C4:1.0, E4:1.0] cur=E4:1.0"));

        runTest("Remove", "REMOVE CURRENT AT HEAD (CURRENT -> NEXT)", listOf(THREE, 0),
                "removeCurrent()",
                "[E4:1.0, G4:2.0] cur=E4:1.0; returns C4:1.0",
                list -> removeCurrentCase(list, C, "[E4:1.0, G4:2.0] cur=E4:1.0"));

        runTest("Remove", "REMOVE CURRENT ON SIZE 2 (TAIL)", listOf(TWO, 1),
                "removeCurrent()",
                "[C4:1.0] cur=C4:1.0; head == tail",
                list -> {
                    removeCurrentCase(list, E, "[C4:1.0] cur=C4:1.0");
                    expectEquals("status", "Size: 1 | Head: C4:1.0 | Tail: C4:1.0 | Current: C4:1.0", list.status());
                });

        runTest("Remove", "REMOVE CURRENT ON SIZE 1 (LIST BECOMES EMPTY)", listOf(C),
                "removeCurrent()",
                "[] cur=null; returns C4:1.0; head = tail = null",
                list -> {
                    removeCurrentCase(list, C, "[] cur=null");
                    expectEquals("status", "Size: 0 | Head: null | Tail: null | Current: null", list.status());
                });

        runTest("Remove", "REMOVE CURRENT ON EMPTY LIST", empty(),
                "removeCurrent()",
                "[] cur=null; returns null, no exception",
                list -> {
                    String removed = list.removeCurrent();
                    step(list, "removeCurrent");
                    expectEquals("returned element", null, removed);
                    expectState(list, "[] cur=null");
                    note("returned null, no exception");
                });

        runTest("Remove", "REMOVEAT ON THE CURRENT NODE (MIDDLE -> NEXT)", listOf(THREE, 1),
                "removeAt(1)",
                "[C4:1.0, G4:2.0] cur=G4:2.0",
                list -> removeAtCase(list, 1, E, "[C4:1.0, G4:2.0] cur=G4:2.0"));

        runTest("Remove", "REMOVEAT ON THE CURRENT NODE (TAIL -> PREV)", listOf(THREE, 2),
                "removeAt(2)",
                "[C4:1.0, E4:1.0] cur=E4:1.0",
                list -> removeAtCase(list, 2, G, "[C4:1.0, E4:1.0] cur=E4:1.0"));

        runTest("Remove", "REMOVEAT ON OTHER NODES KEEPS CURRENT", listOf(THREE, 1),
                "removeAt(0); removeAt(1)",
                "[E4:1.0] cur=E4:1.0",
                list -> {
                    removeAtCase(list, 0, C, "[E4:1.0, G4:2.0] cur=E4:1.0");
                    removeAtCase(list, 1, G, "[E4:1.0] cur=E4:1.0");
                });

        runTest("Remove", "REMOVEAT(0) ON SIZE 2 (HEAD)", listOf(TWO, 0),
                "removeAt(0)",
                "[E4:1.0] cur=E4:1.0; head == tail",
                list -> {
                    removeAtCase(list, 0, C, "[E4:1.0] cur=E4:1.0");
                    expectTrue(list.nodeAt(0).prev == null && list.nodeAt(0).next == null,
                            "remaining node still has a link");
                    note("remaining node has prev == next == null");
                });

        runTest("Remove", "REMOVEAT(0) ON EMPTY LIST", empty(),
                "removeAt(0)",
                "[] cur=null; IndexOutOfBoundsException",
                list -> {
                    expectIOOBE(list, "removeAt(0)", () -> list.removeAt(0));
                    expectState(list, "[] cur=null");
                    note("IndexOutOfBoundsException, list still empty");
                });

        runTest("Remove", "CLEAR RESETS HEAD, TAIL, SIZE AND CURRENT", listOf(THREE, 1),
                "clear()",
                "[] cur=null; Size: 0",
                list -> {
                    list.clear();
                    step(list, "clear");
                    expectState(list, "[] cur=null");
                    expectEquals("status", "Size: 0 | Head: null | Tail: null | Current: null", list.status());
                    note("all fields reset");
                });
    }

    // ==================================================================
    // Index bounds and get()
    // ==================================================================

    private static void boundsTests() {
        runTest("Bounds", "GET OUT OF BOUNDS (NEGATIVE, == SIZE, > SIZE)", listOf(THREE, 1),
                "get(-1); get(3); get(4)",
                "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0; 3 x IndexOutOfBoundsException",
                list -> {
                    expectIOOBE(list, "get(-1)", () -> list.get(-1));
                    expectIOOBE(list, "get(3)", () -> list.get(3));
                    expectIOOBE(list, "get(4)", () -> list.get(4));
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    note("3 x IndexOutOfBoundsException, list unchanged");
                });

        runTest("Bounds", "REMOVEAT OUT OF BOUNDS (NEGATIVE, == SIZE, > SIZE)", listOf(THREE, 1),
                "removeAt(-1); removeAt(3); removeAt(4)",
                "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0; 3 x IndexOutOfBoundsException",
                list -> {
                    expectIOOBE(list, "removeAt(-1)", () -> list.removeAt(-1));
                    expectIOOBE(list, "removeAt(3)", () -> list.removeAt(3));
                    expectIOOBE(list, "removeAt(4)", () -> list.removeAt(4));
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    note("3 x IndexOutOfBoundsException, list unchanged");
                });

        runTest("Bounds", "INSERTAT OUT OF BOUNDS (NEGATIVE, SIZE + 1, > SIZE + 1)", listOf(THREE, 1),
                "insertAt(-1, \"A4:1.0\"); insertAt(4, \"A4:1.0\"); insertAt(10, \"A4:1.0\")",
                "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0; 3 x IndexOutOfBoundsException, 0 nodes allocated",
                list -> {
                    expectIOOBE(list, "insertAt(-1, A4:1.0)", () -> list.insertAt(-1, "A4:1.0"));
                    expectIOOBE(list, "insertAt(4, A4:1.0)", () -> list.insertAt(4, "A4:1.0"));
                    expectIOOBE(list, "insertAt(10, A4:1.0)", () -> list.insertAt(10, "A4:1.0"));
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    note("3 x IndexOutOfBoundsException, list unchanged, 0 nodes allocated"
                            + " (index == size is valid, see the INSERTAT FRONT, MIDDLE AND END test)");
                });

        runTest("Bounds", "GET ON EMPTY LIST", empty(),
                "get(0); get(-1); get(1)",
                "[] cur=null; 3 x IndexOutOfBoundsException",
                list -> {
                    expectIOOBE(list, "get(0) on empty list", () -> list.get(0));
                    expectIOOBE(list, "get(-1) on empty list", () -> list.get(-1));
                    expectIOOBE(list, "get(1) on empty list", () -> list.get(1));
                    expectState(list, "[] cur=null");
                    note("3 x IndexOutOfBoundsException, no NullPointerException");
                });

        runTest("Access", "GET WALKS FROM THE CLOSER END", listOf(SEVEN, 0),
                "get(i) for i = 0..6",
                "C4..B4 returned in order; link hops 0 1 2 3 2 1 0",
                list -> {
                    String letters = "CDEFGAB";
                    StringBuilder hops = new StringBuilder();
                    for (int i = 0; i < 7; i++) {
                        String value = list.get(i);
                        int steps = list.lastWalkSteps;
                        step(list, "get(" + i + ")");
                        expectEquals("get(" + i + ")", letters.charAt(i) + "4:1.0", value);
                        expectEquals("link hops for get(" + i + ")", Math.min(i, 6 - i), steps);
                        hops.append(i == 0 ? "" : " ").append(steps);
                    }
                    note("all 7 values correct; link hops " + hops + " (never more than 3 for size 7)");
                });
    }

    // ==================================================================
    // Navigation
    // ==================================================================

    private static void navigationTests() {
        runTest("Navigation", "NAVIGATION STOPS AT BOTH ENDS", listOf(THREE, 0),
                "moveBackward(); moveForward(); moveForward(); moveForward(); moveBackward()",
                "returns false, true, true, false, true; cur=E4:1.0",
                list -> {
                    StringBuilder results = new StringBuilder();
                    navStep(list, false, false, C, results);
                    navStep(list, true, true, E, results);
                    navStep(list, true, true, G, results);
                    navStep(list, true, false, G, results);
                    navStep(list, false, true, E, results);
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    note("returned " + results + "; stayed put at both ends");
                });

        runTest("Navigation", "NAVIGATION ON SIZE 1", listOf(C),
                "moveForward(); moveBackward()",
                "both return false; cur=C4:1.0",
                list -> {
                    StringBuilder results = new StringBuilder();
                    navStep(list, true, false, C, results);
                    navStep(list, false, false, C, results);
                    expectState(list, "[C4:1.0] cur=C4:1.0");
                    note("returned " + results);
                });

        runTest("Navigation", "NAVIGATION ON EMPTY LIST", empty(),
                "moveForward(); moveBackward(); getCurrent(); getCurrentIndex()",
                "false, false, null, -1; no exception",
                list -> {
                    StringBuilder results = new StringBuilder();
                    navStep(list, true, false, null, results);
                    navStep(list, false, false, null, results);
                    expectEquals("getCurrent()", null, list.getCurrent());
                    expectEquals("getCurrentIndex()", -1, list.getCurrentIndex());
                    note("returned " + results + ", getCurrent() = null, getCurrentIndex() = -1");
                });
    }

    // ==================================================================
    // Playback traversal (the logic behind play all / play from current)
    // ==================================================================

    private static void playbackTests() {
        runTest("Playback", "PLAY ALL VISITS EVERY NODE HEAD TO TAIL", listOf(THREE, 1),
                "traverseAll(recorder)",
                "3 visited: [0] C4:1.0 -> [1] E4:1.0 -> [2] G4:2.0; cur unchanged",
                list -> {
                    String order = playOrder(list, false, -1);
                    expectEquals("visit order", "3 visited: [0] C4:1.0 -> [1] E4:1.0 -> [2] G4:2.0", order);
                    expectState(list, "[C4:1.0, E4:1.0, G4:2.0] cur=E4:1.0");
                    note(order);
                });

        runTest("Playback", "PLAY FROM CURRENT STARTS AT CURRENT", listOf(THREE, 1),
                "traverseFromCurrent(recorder)",
                "2 visited: [1] E4:1.0 -> [2] G4:2.0",
                list -> {
                    String order = playOrder(list, true, -1);
                    expectEquals("visit order", "2 visited: [1] E4:1.0 -> [2] G4:2.0", order);
                    note(order);
                });

        runTest("Playback", "PLAY STOPS EARLY WHEN THE VISITOR RETURNS FALSE", listOf(THREE, 0),
                "traverseAll(recorder that returns false at index 1)",
                "2 visited: [0] C4:1.0 -> [1] E4:1.0",
                list -> {
                    String order = playOrder(list, false, 1);
                    expectEquals("visit order", "2 visited: [0] C4:1.0 -> [1] E4:1.0", order);
                    note(order + " (the driver stops like this if MIDI playback fails)");
                });

        runTest("Playback", "PLAYBACK ON EMPTY LIST PLAYS NOTHING", empty(),
                "traverseAll(recorder); traverseFromCurrent(recorder); getCurrent()",
                "0 visited by both; current null, so play current has nothing to play",
                list -> {
                    String all = playOrder(list, false, -1);
                    String fromCurrent = playOrder(list, true, -1);
                    expectEquals("play all", "0 visited: (none)", all);
                    expectEquals("play from current", "0 visited: (none)", fromCurrent);
                    expectEquals("getCurrent()", null, list.getCurrent());
                    note("play all: " + all + "; play from current: " + fromCurrent + "; getCurrent() = null");
                });
    }

    // ==================================================================
    // Phrase validation
    // ==================================================================

    private static void validationTests() {
        runTest("Validation", "INVALID PHRASES ARE REJECTED AND NOT INSERTED", listOf(C),
                "for 24 malformed phrases: insertLast(p) only if PhraseValidator.validate(p) == null",
                "[C4:1.0] cur=C4:1.0; 24/24 rejected, each message names the bad token",
                list -> {
                    counter = 0;
                    reject(list, "C4:abc", "C4:abc");                 // would crash MidiPlayer
                    reject(list, "C4:1.0 E4:abc G4:1.0", "E4:abc");   // second token is the bad one
                    reject(list, "H4:1.0", "H4:1.0");
                    reject(list, "c4:1.0", "c4:1.0");
                    reject(list, "R4:1.0", "R4:1.0");
                    reject(list, "C:1.0", "C:1.0");
                    reject(list, "C10:1.0", "C10:1.0");
                    reject(list, "C#b4:1.0", "C#b4:1.0");
                    reject(list, "G#9:1.0", "G#9:1.0");
                    reject(list, "C4+:1.0", "C4+:1.0");
                    reject(list, "C4+R:1.0", "C4+R:1.0");
                    reject(list, "C4", "C4");
                    reject(list, "C4:1.0:2", "C4:1.0:2");
                    reject(list, ":1.0", ":1.0");
                    reject(list, "C4:", "C4:");
                    reject(list, "C4:0", "C4:0");
                    reject(list, "C4:0.0", "C4:0.0");
                    reject(list, "C4:-1", "C4:-1");
                    reject(list, "C4:1e3", "C4:1e3");
                    reject(list, "C4:NaN", "C4:NaN");
                    reject(list, "C4:1..5", "C4:1..5");
                    reject(list, "C4:100", "C4:100");
                    reject(list, "", null);
                    reject(list, "   ", null);
                    expectState(list, "[C4:1.0] cur=C4:1.0");
                    note(counter + "/24 rejected, each message names the bad token (listed before the summary); list unchanged");
                });

        runTest("Validation", "VALID PHRASES (SHARPS, FLATS, RESTS, CHORDS) ARE ACCEPTED", empty(),
                "for 8 valid phrases: insertLast(normalize(p)) after validate(p) == null",
                "[C4:1.0, D#4:0.5, Eb5:0.25, R:0.5, C4+E4+G4:1.0, A4:0.5 Bb3:.5, G9:1, Cb0:60] cur=C4:1.0",
                list -> {
                    counter = 0;
                    accept(list, "C4:1.0", "C4:1.0");
                    accept(list, "D#4:0.5", "D#4:0.5");
                    accept(list, "Eb5:0.25", "Eb5:0.25");
                    accept(list, "R:0.5", "R:0.5");
                    accept(list, "C4+E4+G4:1.0", "C4+E4+G4:1.0");
                    accept(list, "  A4:0.5    Bb3:.5  ", "A4:0.5 Bb3:.5");
                    accept(list, "G9:1", "G9:1");
                    accept(list, "Cb0:60", "Cb0:60");
                    expectState(list, "[C4:1.0, D#4:0.5, Eb5:0.25, R:0.5, C4+E4+G4:1.0, A4:0.5 Bb3:.5, G9:1, Cb0:60] cur=C4:1.0");
                    note(counter + "/8 accepted; extra whitespace normalized");
                });

        runTest("Validation", "DEMO COMPOSITION LOADS AND EVERY PHRASE IS VALID", listOf(C),
                "Main.loadDemoComposition(list)",
                "5 valid phrases; old content replaced; cur = first phrase (head)",
                list -> {
                    Main.loadDemoComposition(list);
                    step(list, "loadDemoComposition");
                    expectEquals("size", 5, list.size());
                    for (int i = 0; i < list.size(); i++) {
                        String error = PhraseValidator.validate(list.get(i));
                        expectTrue(error == null, "demo phrase " + i + " is invalid: " + error);
                    }
                    expectTrue(list.currentNode() == list.nodeAt(0), "current is not the head");
                    note("5 phrases, all pass PhraseValidator; current = head");
                });
    }

    // ==================================================================
    // Test framework
    // ==================================================================

    private static void runTest(String category, String title, Setup setup, String executing,
                                String expected, Action action) {
        testCount++;
        currentId = "T-" + (testCount < 10 ? "0" : "") + testCount;
        notes = new StringBuilder();
        log("=== RUNNING TEST " + currentId + ": " + title + " ===");

        PhraseList<String> list;
        try {
            list = setup.build();
        } catch (RuntimeException e) {
            log("Executing: " + executing);
            finish(category, title, "(setup failed)", executing, expected, "(not run)",
                    "setup failed: " + e.getMessage());
            return;
        }

        String initial = compact(list);
        log("Initial List: " + list);
        log(list.status());
        log("Executing: " + executing);

        String failure = null;
        try {
            action.run(list);
            if (!list.checkInvariants()) {
                failure = "invariant broken after the operation: " + list.describeInvariantViolation();
            }
        } catch (TestFailure f) {
            failure = f.getMessage();
        } catch (RuntimeException e) {
            failure = "unexpected exception: " + e;
        }

        // Printing walks the links, which would loop forever on a corrupt
        // (cyclic) list, so print only after the bounded invariant check passes.
        String violation = list.describeInvariantViolation();
        String actual;
        if (violation == null) {
            log("Result List: " + list);
            log(list.status());
            log(list.backwardTrace());
            actual = compact(list) + (notes.length() > 0 ? "; " + notes : "");
        } else {
            log("Result List: (not printed, the links are corrupt: " + violation + ")");
            log(list.status());
            log("Check Backwards Link (Tail to Head): (not printed, the links are corrupt)");
            actual = "(corrupt list)";
            if (failure == null) {
                failure = "invariant broken after the operation: " + violation;
            }
        }
        finish(category, title, initial, executing, expected, actual, failure);
    }

    private static void finish(String category, String title, String initial, String executing,
                               String expected, String actual, String failure) {
        log(failure == null ? "[SUCCESS]" : "[FAILED: " + failure + "]");
        log("");
        if (failure == null) {
            passed++;
        } else {
            failed++;
        }
        SUMMARY.append(currentId).append(failure == null ? "  [SUCCESS]  " : "  [FAILED]   ")
                .append(title).append('\n');
        MATRIX.append("| ").append(currentId)
                .append(" | ").append(cell(category))
                .append(" | ").append(cell(initial))
                .append(" | ").append(cell(executing))
                .append(" | ").append(cell(expected))
                .append(" | ").append(cell(failure == null ? actual : actual + " -- " + failure))
                .append(" | ").append(failure == null ? "PASS" : "FAIL")
                .append(" |\n");
    }

    // ---------------------------- setup -------------------------------

    private static Setup empty() {
        return () -> new PhraseList<String>();
    }

    private static Setup listOf(String csv) {
        return listOf(csv, 0);
    }

    /** Builds a list from comma-separated phrases with current at currentIndex. */
    private static Setup listOf(final String csv, final int currentIndex) {
        return () -> {
            PhraseList<String> list = new PhraseList<String>();
            int start = 0;
            while (start < csv.length()) {
                int comma = csv.indexOf(',', start);
                int end = comma < 0 ? csv.length() : comma;
                String phrase = csv.substring(start, end).trim();
                list.insertLast(phrase);
                step(list, "insertLast(\"" + phrase + "\")");
                start = end + 1;
            }
            for (int i = 0; i < currentIndex; i++) {
                if (!list.moveForward()) {
                    throw new TestFailure("could not move current to index " + currentIndex);
                }
                step(list, "moveForward()");
            }
            return list;
        };
    }

    // --------------------------- checks -------------------------------

    /** Runs checkInvariants() and fails the test if any invariant is broken. */
    private static void step(PhraseList<String> list, String afterWhat) {
        if (!list.checkInvariants()) {
            throw new TestFailure("invariant broken after " + afterWhat + ": " + list.describeInvariantViolation());
        }
    }

    private static void expectState(PhraseList<String> list, String expected) {
        String actual = compact(list);
        if (!actual.equals(expected)) {
            throw new TestFailure("expected " + expected + " but got " + actual);
        }
    }

    private static void expectEquals(String what, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        if (!same) {
            throw new TestFailure(what + ": expected " + expected + " but got " + actual);
        }
    }

    private static void expectTrue(boolean condition, String message) {
        if (!condition) {
            throw new TestFailure(message);
        }
    }

    /**
     * Expects {@code call} to throw IndexOutOfBoundsException with a message
     * and to leave the list exactly as it was (same text, same nodes, no new
     * node allocated).
     */
    private static void expectIOOBE(PhraseList<String> list, String description, Runnable call) {
        String before = list.toString() + " / " + list.status();
        Snapshot snap = snapshot(list);
        int created = Node.instancesCreated;
        try {
            call.run();
        } catch (IndexOutOfBoundsException e) {
            int allocated = Node.instancesCreated - created;
            step(list, "rejected " + description);
            String message = e.getMessage();
            expectTrue(message != null && !message.isEmpty(),
                    description + " threw IndexOutOfBoundsException without a message");
            expectEquals("list after rejected " + description, before, list.toString() + " / " + list.status());
            expectNodeOrder(list, snap, identityOrder(list.size()));
            expectEquals("nodes allocated by rejected " + description, 0, allocated);
            MESSAGES.append(currentId).append("  ").append(description)
                    .append(" -> IndexOutOfBoundsException: ").append(message).append('\n');
            return;
        } catch (RuntimeException e) {
            throw new TestFailure(description + " threw " + e.getClass().getSimpleName()
                    + " instead of IndexOutOfBoundsException");
        }
        throw new TestFailure(description + " did not throw IndexOutOfBoundsException");
    }

    /** Runs one move and checks state, node identity, allocations and the cursor. */
    private static void moveCase(PhraseList<String> list, int source, int target,
                                 String expectedState, String expectedOrder) {
        Snapshot snap = snapshot(list);
        Node<String> currentBefore = list.currentNode();
        int created = Node.instancesCreated;
        boolean changed = list.move(source, target);
        int allocated = Node.instancesCreated - created;
        step(list, "move(" + source + ", " + target + ")");
        expectTrue(changed, "move(" + source + ", " + target + ") returned false");
        expectState(list, expectedState);
        expectNodeOrder(list, snap, expectedOrder);
        expectEquals("nodes allocated by move", 0, allocated);
        expectTrue(list.currentNode() == currentBefore, "current no longer points to the same node");
        note("same " + list.size() + " node objects now in original order " + expectedOrder
                + "; 0 nodes allocated; no data swapped; current is the same node");
    }

    private static void removeCurrentCase(PhraseList<String> list, String expectedRemoved, String expectedState) {
        String removed = list.removeCurrent();
        step(list, "removeCurrent()");
        expectEquals("returned element", expectedRemoved, removed);
        expectState(list, expectedState);
        note("returned " + removed);
    }

    private static void removeAtCase(PhraseList<String> list, int index, String expectedRemoved, String expectedState) {
        String removed = list.removeAt(index);
        step(list, "removeAt(" + index + ")");
        expectEquals("removeAt(" + index + ") returned", expectedRemoved, removed);
        expectState(list, expectedState);
        note("removeAt(" + index + ") returned " + removed);
    }

    private static void navStep(PhraseList<String> list, boolean forward, boolean expectedResult,
                                String expectedCurrent, StringBuilder results) {
        String call = forward ? "moveForward()" : "moveBackward()";
        boolean result = forward ? list.moveForward() : list.moveBackward();
        step(list, call);
        expectEquals(call + " return value", expectedResult, result);
        expectEquals("current after " + call, expectedCurrent, list.getCurrent());
        results.append(results.length() == 0 ? "" : ", ").append(result);
    }

    /**
     * Records which nodes a traversal visits, e.g. "2 visited: [1] E4:1.0 -> [2] G4:2.0".
     * If stopAt >= 0 the recorder returns false at that index.
     */
    private static String playOrder(PhraseList<String> list, boolean fromCurrent, final int stopAt) {
        final StringBuilder sb = new StringBuilder();
        PhraseList.Visitor<String> recorder = (index, phrase) -> {
            sb.append(sb.length() == 0 ? "" : " -> ").append('[').append(index).append("] ").append(phrase);
            return index != stopAt;
        };
        int visited = fromCurrent ? list.traverseFromCurrent(recorder) : list.traverseAll(recorder);
        step(list, fromCurrent ? "traverseFromCurrent" : "traverseAll");
        return visited + " visited: " + (sb.length() == 0 ? "(none)" : sb.toString());
    }

    /** Mirrors the driver: a phrase is inserted only if it validates. */
    private static void reject(PhraseList<String> list, String phrase, String badToken) {
        String error = PhraseValidator.validate(phrase);
        if (error == null) {
            list.insertLast(phrase);
            step(list, "insertLast");
            throw new TestFailure("\"" + phrase + "\" was accepted and inserted");
        }
        step(list, "validate(\"" + phrase + "\")");
        if (badToken != null) {
            expectTrue(error.contains("'" + badToken + "'"),
                    "message for \"" + phrase + "\" does not name '" + badToken + "': " + error);
        }
        counter++;
        MESSAGES.append(currentId).append("  \"").append(phrase).append("\" -> ").append(error).append('\n');
    }

    private static void accept(PhraseList<String> list, String phrase, String expectedStored) {
        String error = PhraseValidator.validate(phrase);
        expectTrue(error == null, "\"" + phrase + "\" was rejected: " + error);
        String stored = PhraseValidator.normalize(phrase);
        list.insertLast(stored);
        step(list, "insertLast(\"" + stored + "\")");
        expectEquals("stored phrase", expectedStored, stored);
        counter++;
    }

    // ------------------------ node identity ---------------------------

    private static Snapshot snapshot(PhraseList<String> list) {
        Snapshot snap = new Snapshot();
        for (int i = 0; i < list.size(); i++) {
            Node<String> node = list.nodeAt(i);
            snap.nodes.insertLast(node);
            snap.data.insertLast(node.data);
        }
        return snap;
    }

    /**
     * Checks that position p of the list holds original node order[p] (the
     * same object), and that no original node had its data replaced.
     *
     * @param order space-separated original indices, e.g. "2 0 1"
     */
    private static void expectNodeOrder(PhraseList<String> list, Snapshot snap, String order) {
        PhraseList<Integer> expected = parseInts(order);
        expectEquals("node count", expected.size(), list.size());
        for (int pos = 0; pos < expected.size(); pos++) {
            int original = expected.get(pos);
            if (list.nodeAt(pos) != snap.nodes.get(original)) {
                throw new TestFailure("position " + pos + " should hold original node #" + original
                        + " (" + snap.data.get(original) + ") but holds a different Node object");
            }
        }
        for (int k = 0; k < snap.nodes.size(); k++) {
            if (snap.nodes.get(k).data != snap.data.get(k)) {
                throw new TestFailure("the data inside original node #" + k + " was replaced (data swap)");
            }
        }
    }

    private static String identityOrder(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(i == 0 ? "" : " ").append(i);
        }
        return sb.toString();
    }

    private static PhraseList<Integer> parseInts(String text) {
        PhraseList<Integer> values = new PhraseList<Integer>();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') {
                i++;
            }
            int start = i;
            while (i < text.length() && text.charAt(i) != ' ') {
                i++;
            }
            if (i > start) {
                values.insertLast(Integer.valueOf(text.substring(start, i)));
            }
        }
        return values;
    }

    // --------------------------- output -------------------------------

    /** Compact one-line state used in the matrix: "[C4:1.0, E4:1.0] cur=E4:1.0". */
    private static String compact(PhraseList<String> list) {
        final StringBuilder sb = new StringBuilder("[");
        list.traverseAll((index, phrase) -> {
            sb.append(index == 0 ? "" : ", ").append(phrase);
            return true;
        });
        return sb.append("] cur=").append(list.getCurrent()).toString();
    }

    private static void note(String text) {
        notes.append(notes.length() == 0 ? "" : "; ").append(text);
    }

    private static String cell(String text) {
        return text.replace("|", "\\|");
    }

    private static void log(String line) {
        System.out.println(line);
        LOG.append(line).append('\n');
    }

    private static void writeFile(String name, String content) {
        try (Writer out = new FileWriter(name)) {
            out.write(content);
        } catch (IOException e) {
            System.out.println("[!] Could not write " + name + ": " + e.getMessage());
        }
    }
}
