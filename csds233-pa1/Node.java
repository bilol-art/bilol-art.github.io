/**
 * CSDS 233 - Programming Assignment 1
 *
 * One node of the doubly linked list used by {@link PhraseList}. Each node
 * stores one element (a musical phrase in this project) plus explicit links
 * to its two neighbours.
 *
 * <p>The fields are package-private on purpose: only {@link PhraseList} (and
 * the white-box tests in {@link TestRunner}) should touch the links.</p>
 *
 * @param <E> type of the stored element
 */
class Node<E> {

    /**
     * Number of Node objects created so far. Package-private test hook: the
     * tests read it before and after {@code move()} to prove that moving a
     * phrase allocates no new node.
     */
    static int instancesCreated = 0;

    /** The element stored in this node. */
    E data;

    /** Link to the previous node, or {@code null} if this node is the head. */
    Node<E> prev;

    /** Link to the next node, or {@code null} if this node is the tail. */
    Node<E> next;

    /**
     * Creates an unlinked node.
     *
     * @param data the element to store
     */
    Node(E data) {
        this.data = data;
        this.prev = null;
        this.next = null;
        instancesCreated++;
    }
}
