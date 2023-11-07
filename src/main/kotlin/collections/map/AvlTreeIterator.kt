package collections.map

import java.util.*

/**
 * Iterate on a Tree.
 * Left => current => right
 *
 * @param <K>
 * @param <T>
</T></K> */
class AvlTreeIterator<K : Comparable<K>, T>(
    private val localRoot: INode<K, T>,
    private val startKey: K?,
    private val endKey: K?
) :
    Iterator<INode<K, T>> {

    private var nextElement: INode<K, T>?
    private var currentDone = false
    private val childsIterator: Array<Iterator<INode<K, T>>?> = arrayOfNulls<Iterator<INode<K, T>>?>(2)
    private val testedChild = booleanArrayOf(false, false)

    init {
        nextElement = searchNext()
    }

    override fun hasNext(): Boolean {
        return nextElement != null
    }

    override fun next(): INode<K, T> {
        val next = nextElement
        if (next == null) {
            throw NoSuchElementException("iterator ended")
        }
        nextElement = searchNext()
        return next
    }

    private fun searchNext(): INode<K, T>? {
        initChildIterator(0)
        val iteratorLeft = childsIterator[0]
        if (iteratorLeft != null && iteratorLeft.hasNext()) {
            return iteratorLeft.next()
        }
        if (!currentDone) {
            currentDone = true
            val key: K = localRoot.key
            if ((startKey == null || startKey.compareTo(key) <= 0) &&
                (endKey == null || endKey.compareTo(key) >= 0)
            ) {
                return localRoot
            }
        }
        initChildIterator(1)
        val iteratorRight = childsIterator[1]
        return if (iteratorRight != null && iteratorRight.hasNext()) {
            iteratorRight.next()
        } else {
            null
        }
    }

    private fun initChildIterator(childNum: Int) {
        if (!testedChild[childNum]) {
            val child = localRoot.getChild(childNum)
            var explore = child != null
            if (explore && childNum == 0) {
                // Explore left subtree where (key <= localRoot.key) only if localroot.key >= start key
                // (otherwise, all key of left sub-tree can't be valid).
                explore = startKey == null || startKey.compareTo(localRoot.key) <= 0
            } else if (explore) {
                // Explore right subtree where (key >= localRoot.key) only if localroot.key <= end key
                // (otherwise, all key of right sub-tree can't be valid).
                explore = endKey == null || endKey.compareTo(localRoot.key) >= 0
            }
            if (explore && child != null) {
                childsIterator[childNum] = AvlTreeIterator<K, T>(child, startKey, endKey)
            }
            testedChild[childNum] = true
        }
    }

    class IteratorBuilder<K : Comparable<K>, T> internal constructor(private val root: INode<K, T>?) {
        private var startKey: K? = null

        private var endKey: K? = null

        fun build(): Iterator<INode<K, T>> {
            val r = this.root
            return if (r == null) {
                Collections.emptyIterator()
            } else {
                AvlTreeIterator(r, startKey, endKey)
            }
        }

        fun startAt(startKey: K): IteratorBuilder<K, T> {
            this.startKey = startKey
            return this
        }

        fun endAt(endKey: K): IteratorBuilder<K, T> {
            this.endKey = endKey
            return this
        }
    }
}
