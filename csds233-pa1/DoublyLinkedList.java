/**
 * CSDS 233 - Programming Assignment 1
 *
 * The doubly linked list ADT required by the assignment. All indices are
 * zero-based.
 *
 * @param <E> element type
 */
public interface DoublyLinkedList<E> {

    /**
     * Inserts an element at the front of the list (index 0).
     *
     * @param element the element to insert
     */
    void insertFirst(E element);

    /**
     * Appends an element at the end of the list (index size()).
     *
     * @param element the element to insert
     */
    void insertLast(E element);

    /**
     * Inserts an element so that it ends up at position {@code index}.
     *
     * @param index   final position of the new element, 0..size()
     * @param element the element to insert
     * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt; size()
     */
    void insertAt(int index, E element);

    /**
     * Removes the element at {@code index} and returns it.
     *
     * @param index position of the element, 0..size()-1
     * @return the removed element
     * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= size()
     */
    E removeAt(int index);

    /**
     * Returns the element at {@code index} without removing it.
     *
     * @param index position of the element, 0..size()-1
     * @return the element at that position
     * @throws IndexOutOfBoundsException if index &lt; 0 or index &gt;= size()
     */
    E get(int index);

    /**
     * @return the number of elements in the list
     */
    int size();

    /**
     * @return true if the list holds no elements
     */
    boolean isEmpty();
}
