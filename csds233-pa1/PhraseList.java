/**
 * CSDS 233 - Programming Assignment 1
 *
 * A doubly linked list of musical phrases with a built-in "current" cursor.
 *
 * <p>Class invariants, verified by {@link #checkInvariants()}:</p>
 * <ul>
 *   <li>{@code head.prev == null} and {@code tail.next == null};</li>
 *   <li>for every node {@code a} that has a successor, {@code a.next.prev == a};</li>
 *   <li>walking forward from head and backward from tail both visit exactly
 *       {@code size} nodes;</li>
 *   <li>{@code current == null} if and only if the list is empty; otherwise
 *       {@code current} is one of the nodes in the list.</li>
 * </ul>
 *
 * <p>The cursor lives in this class rather than in the driver because only
 * this class can see the links. When the current node is unlinked, the
 * cursor has to move to a neighbour in the same step, or it would point at a
 * node that is no longer in the list.</p>
 *
 * <p>{@code null} elements are rejected, so a {@code null} return value
 * always means "no element".</p>
 *
 * @param <E> element type (String phrases in the sequencer)
 */
public class PhraseList<E> implements DoublyLinkedList<E> {

    /**
     * Callback for walking the list from front to back without exposing its
     * nodes. The driver uses it for playback; the tests use it to record the
     * visiting order. A visitor must not modify the list it is visiting.
     *
     * @param <E> element type
     */
    public interface Visitor<E> {
        /**
         * Called once for each visited element.
         *
         * @param index   zero-based position of the element in the list
         * @param element the element
         * @return true to continue with the next node, false to stop early
         */
        boolean visit(int index, E element);
    }

    private Node<E> head;
    private Node<E> tail;
    private Node<E> current;
    private int size;

    /**
     * Package-private test hook: number of link hops taken by the most recent
     * index lookup. Lets the tests check that lookups start from the closer end.
     */
    int lastWalkSteps;

    /** Creates an empty list. */
    public PhraseList() {
        head = null;
        tail = null;
        current = null;
        size = 0;
    }

    // ------------------------------------------------------------------
    // DoublyLinkedList interface
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc} If the list was empty, the new node also becomes current.
     */
    @Override
    public void insertFirst(E element) {
        Node<E> node = newNode(element);
        if (isEmpty()) {
            linkIntoEmpty(node);
        } else {
            linkBefore(node, head);
        }
    }

    /**
     * {@inheritDoc} If the list was empty, the new node also becomes current.
     */
    @Override
    public void insertLast(E element) {
        Node<E> node = newNode(element);
        if (isEmpty()) {
            linkIntoEmpty(node);
        } else {
            linkAfter(node, tail);
        }
    }

    /**
     * {@inheritDoc} Valid indices are 0..size(). If the list was empty, the
     * new node also becomes current; otherwise current does not change.
     */
    @Override
    public void insertAt(int index, E element) {
        checkPositionIndex(index);
        if (index == size) {
            insertLast(element);
        } else {
            linkBefore(newNode(element), nodeAt(index));
        }
    }

    /**
     * {@inheritDoc} If the removed node was current, current moves to the next
     * node, or to the previous node if the tail was removed, or to null if the
     * list became empty.
     */
    @Override
    public E removeAt(int index) {
        return unlink(nodeAt(index));
    }

    /** {@inheritDoc} The walk starts from whichever end is closer. */
    @Override
    public E get(int index) {
        return nodeAt(index).data;
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return size;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    // ------------------------------------------------------------------
    // Current-pointer operations
    // ------------------------------------------------------------------

    /**
     * @return the element of the current node, or null if the list is empty
     */
    public E getCurrent() {
        return current == null ? null : current.data;
    }

    /**
     * @return the zero-based index of the current node, or -1 if the list is empty
     */
    public int getCurrentIndex() {
        int index = 0;
        for (Node<E> n = head; n != null; n = n.next) {
            if (n == current) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /**
     * Inserts an element right after the current node. Current does not move.
     * On an empty list the new node becomes the only node and the current one.
     *
     * @param element the element to insert
     */
    public void insertAfterCurrent(E element) {
        Node<E> node = newNode(element);
        if (isEmpty()) {
            linkIntoEmpty(node);
        } else {
            linkAfter(node, current);
        }
    }

    /**
     * Appends a copy of the current phrase (a new node holding the same
     * element) to the end of the list. Current does not move.
     *
     * @return true if a copy was appended, false if the list is empty
     */
    public boolean repeatCurrent() {
        if (current == null) {
            return false;
        }
        linkAfter(newNode(current.data), tail);
        return true;
    }

    /**
     * Removes the current node. Current then moves to the next node, or to the
     * previous node if the tail was removed, or to null if the list became empty.
     *
     * @return the removed element, or null if the list was empty (nothing removed)
     */
    public E removeCurrent() {
        if (current == null) {
            return null;
        }
        return unlink(current);
    }

    /**
     * Moves the cursor one node towards the tail.
     *
     * @return true if it moved, false if the list is empty or current is the tail
     */
    public boolean moveForward() {
        if (current == null || current.next == null) {
            return false;
        }
        current = current.next;
        return true;
    }

    /**
     * Moves the cursor one node towards the head.
     *
     * @return true if it moved, false if the list is empty or current is the head
     */
    public boolean moveBackward() {
        if (current == null || current.prev == null) {
            return false;
        }
        current = current.prev;
        return true;
    }

    /**
     * Moves the node at {@code sourceIndex} so that it ends up at position
     * {@code targetIndex}, by rewiring prev/next links only. No node is
     * created and no data is swapped, so current keeps pointing at the same
     * node (its index may change).
     *
     * <p>Example: [C4:1.0, E4:1.0, G4:2.0], move(2, 0) gives
     * [G4:2.0, C4:1.0, E4:1.0].</p>
     *
     * @param sourceIndex current position of the node, 0..size()-1
     * @param targetIndex final position of the node, 0..size()-1
     * @return true if the list changed, false if sourceIndex == targetIndex
     * @throws IndexOutOfBoundsException if either index is invalid (the list
     *                                   is left unchanged)
     */
    public boolean move(int sourceIndex, int targetIndex) {
        checkElementIndex(sourceIndex, "Source index");
        checkElementIndex(targetIndex, "Target index");
        if (sourceIndex == targetIndex) {
            return false;
        }

        Node<E> node = nodeAt(sourceIndex);
        // 1. Splice the node out. The other size-1 nodes stay correctly linked.
        detach(node);
        // 2. Splice it back in so that it ends up at targetIndex.
        if (targetIndex == size) {
            linkAfter(node, tail);          // final position is the new tail
        } else {
            linkBefore(node, nodeAt(targetIndex));
        }
        return true;
    }

    /** Removes every element. Head, tail and current become null and size 0. */
    public void clear() {
        head = null;
        tail = null;
        current = null;
        size = 0;
    }

    // ------------------------------------------------------------------
    // Traversal (used for playback)
    // ------------------------------------------------------------------

    /**
     * Visits every element from head to tail.
     *
     * @param visitor callback invoked for each element
     * @return the number of elements visited
     */
    public int traverseAll(Visitor<? super E> visitor) {
        return traverse(head, 0, visitor);
    }

    /**
     * Visits the elements from the current node to the tail.
     *
     * @param visitor callback invoked for each element
     * @return the number of elements visited (0 for an empty list)
     */
    public int traverseFromCurrent(Visitor<? super E> visitor) {
        int startIndex = current == null ? 0 : getCurrentIndex();
        return traverse(current, startIndex, visitor);
    }

    private int traverse(Node<E> start, int startIndex, Visitor<? super E> visitor) {
        if (visitor == null) {
            throw new IllegalArgumentException("visitor must not be null");
        }
        int visited = 0;
        for (Node<E> n = start; n != null; n = n.next) {
            visited++;
            if (!visitor.visit(startIndex + visited - 1, n.data)) {
                break;
            }
        }
        return visited;
    }

    // ------------------------------------------------------------------
    // Printing and self-checks
    // ------------------------------------------------------------------

    /**
     * @return the list from head to tail, e.g.
     *         {@code [ C4:1.0 ] <-> [ E4:1.0 ] <-> [ G4:2.0 ]}, or
     *         {@code (empty)}
     */
    @Override
    public String toString() {
        if (head == null) {
            return "(empty)";
        }
        StringBuilder sb = new StringBuilder();
        for (Node<E> n = head; n != null; n = n.next) {
            if (n != head) {
                sb.append(" <-> ");
            }
            sb.append("[ ").append(n.data).append(" ]");
        }
        return sb.toString();
    }

    /**
     * @return a one-line summary, e.g.
     *         {@code Size: 3 | Head: C4:1.0 | Tail: G4:2.0 | Current: E4:1.0}
     */
    public String status() {
        return "Size: " + size
                + " | Head: " + dataOf(head)
                + " | Tail: " + dataOf(tail)
                + " | Current: " + dataOf(current);
    }

    /**
     * Follows the prev links from tail to head, which proves the backward
     * links are intact.
     *
     * @return e.g. {@code Check Backwards Link (Tail to Head): G4:2.0 -> E4:1.0 -> C4:1.0 -> null}
     */
    public String backwardTrace() {
        StringBuilder sb = new StringBuilder("Check Backwards Link (Tail to Head): ");
        for (Node<E> n = tail; n != null; n = n.prev) {
            sb.append(n.data).append(" -> ");
        }
        return sb.append("null").toString();
    }

    /**
     * Checks every structural invariant of the list (see class comment).
     *
     * @return true if all invariants hold
     */
    public boolean checkInvariants() {
        return describeInvariantViolation() == null;
    }

    /**
     * Package-private: the first broken invariant, so tests can report why
     * {@link #checkInvariants()} failed.
     *
     * @return a description of the first violated invariant, or null if none
     */
    String describeInvariantViolation() {
        if (size < 0) {
            return "size is negative (" + size + ")";
        }
        if (size == 0) {
            if (head != null) {
                return "size is 0 but head is not null";
            }
            if (tail != null) {
                return "size is 0 but tail is not null";
            }
            if (current != null) {
                return "list is empty but current is not null";
            }
            return null;
        }
        if (head == null || tail == null) {
            return "size is " + size + " but head or tail is null";
        }
        if (current == null) {
            return "list is not empty but current is null";
        }
        if (head.prev != null) {
            return "head.prev is not null";
        }
        if (tail.next != null) {
            return "tail.next is not null";
        }

        // Forward walk: count nodes, check a.next.prev == a, look for current.
        int forward = 0;
        boolean currentFound = false;
        Node<E> last = null;
        for (Node<E> n = head; n != null; n = n.next) {
            forward++;
            if (forward > size) {
                return "forward walk visits more than size=" + size + " nodes (cycle or wrong size)";
            }
            if (n.data == null) {
                return "node " + (forward - 1) + " holds null data";
            }
            if (n.next != null && n.next.prev != n) {
                return "node " + (forward - 1) + ": next.prev does not point back to it";
            }
            if (n == current) {
                currentFound = true;
            }
            last = n;
        }
        if (forward != size) {
            return "forward walk counted " + forward + " nodes but size is " + size;
        }
        if (last != tail) {
            return "forward walk does not end at tail";
        }

        // Backward walk: count nodes, check a.prev.next == a.
        int backward = 0;
        Node<E> first = null;
        for (Node<E> n = tail; n != null; n = n.prev) {
            backward++;
            if (backward > size) {
                return "backward walk visits more than size=" + size + " nodes (cycle or wrong size)";
            }
            if (n.prev != null && n.prev.next != n) {
                return "node " + (size - backward) + ": prev.next does not point back to it";
            }
            first = n;
        }
        if (backward != size) {
            return "backward walk counted " + backward + " nodes but size is " + size;
        }
        if (first != head) {
            return "backward walk does not end at head";
        }
        if (!currentFound) {
            return "current points to a node that is not in the list";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Package-private hooks for white-box tests
    // ------------------------------------------------------------------

    /**
     * Returns the node object at {@code index}, walking from the closer end.
     * Package-private so tests can compare node identity before and after
     * {@link #move(int, int)}.
     *
     * @param index position, 0..size()-1
     * @return the node at that position
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    Node<E> nodeAt(int index) {
        checkElementIndex(index, "Index");
        Node<E> node;
        int steps = 0;
        if (index < (size >> 1)) {
            node = head;
            for (int i = 0; i < index; i++) {
                node = node.next;
                steps++;
            }
        } else {
            node = tail;
            for (int i = size - 1; i > index; i--) {
                node = node.prev;
                steps++;
            }
        }
        lastWalkSteps = steps;
        return node;
    }

    /**
     * @return the current node object (null if the list is empty); test hook
     */
    Node<E> currentNode() {
        return current;
    }

    // ------------------------------------------------------------------
    // Private helpers: every link change goes through these
    // ------------------------------------------------------------------

    private Node<E> newNode(E element) {
        if (element == null) {
            throw new IllegalArgumentException("Phrase list elements must not be null");
        }
        return new Node<E>(element);
    }

    /** Makes {@code node} the only node; it becomes head, tail and current. */
    private void linkIntoEmpty(Node<E> node) {
        node.prev = null;
        node.next = null;
        head = node;
        tail = node;
        current = node;
        size = 1;
    }

    /** Links an unlinked {@code node} directly in front of {@code succ}. */
    private void linkBefore(Node<E> node, Node<E> succ) {
        Node<E> pred = succ.prev;
        node.prev = pred;
        node.next = succ;
        succ.prev = node;
        if (pred == null) {
            head = node;
        } else {
            pred.next = node;
        }
        size++;
    }

    /** Links an unlinked {@code node} directly after {@code pred}. */
    private void linkAfter(Node<E> node, Node<E> pred) {
        Node<E> succ = pred.next;
        node.prev = pred;
        node.next = succ;
        pred.next = node;
        if (succ == null) {
            tail = node;
        } else {
            succ.prev = node;
        }
        size++;
    }

    /**
     * Splices {@code node} out of the chain and fixes head/tail. Does not
     * touch current: callers decide what happens to the cursor.
     */
    private void detach(Node<E> node) {
        Node<E> pred = node.prev;
        Node<E> succ = node.next;
        if (pred == null) {
            head = succ;
        } else {
            pred.next = succ;
        }
        if (succ == null) {
            tail = pred;
        } else {
            succ.prev = pred;
        }
        node.prev = null;
        node.next = null;
        size--;
    }

    /** Removes {@code node} for good, moving the cursor first if needed. */
    private E unlink(Node<E> node) {
        if (node == current) {
            current = (node.next != null) ? node.next : node.prev;
        }
        detach(node);
        return node.data;
    }

    /** Valid element indices are 0..size-1. */
    private void checkElementIndex(int index, String what) {
        if (index < 0 || index >= size) {
            if (size == 0) {
                throw new IndexOutOfBoundsException(
                        what + " " + index + " is out of bounds: the list is empty");
            }
            throw new IndexOutOfBoundsException(
                    what + " " + index + " is out of bounds for size " + size
                            + " (valid range: 0.." + (size - 1) + ")");
        }
    }

    /** Valid insertion positions are 0..size. */
    private void checkPositionIndex(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException(
                    "Insert index " + index + " is out of bounds for size " + size
                            + " (valid range: 0.." + size + ")");
        }
    }

    private static String dataOf(Node<?> node) {
        return node == null ? "null" : String.valueOf(node.data);
    }
}
