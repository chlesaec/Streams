package collections.map

import kotlin.math.max

/**
 * Node of AvlTree
 *
 * @param <K> : Class for Key.
 * @param <T> : Class for stored data.
</T></K> */
interface INode<K : Comparable<K>, T> {
    val key: K
    val data: () -> T

    operator fun get(key: K): INode<K, T>?
    fun getChild(num: Int): INode<K, T>?
}

interface NodeLink<K : Comparable<K>, T> {
    var node: Node<K, T>
}

class Node<K : Comparable<K>, T>(
    val link: NodeLink<K, T>,
    override val key: K,
    override val data: () -> T
) :
    INode<K, T> {

    var height: Int = 1

    private val childs: Array<NodeLink<K, T>?> = arrayOfNulls(2)

    private fun updateHeight() {
        val h: Int = max(height(childs[0]), height(childs[1])) + 1
        if (height != h) {
            height = h
        }
    }

    override fun getChild(num: Int): Node<K, T>? {
        return childs[num]?.node
    }

    fun setChild(num: Int, child: Node<K, T>?) {
        childs[num] = child?.link
        updateHeight()
        link.node = this
    }

    fun setChildSimple(num: Int, child: NodeLink<K, T>) {
        childs[num] = child
    }

    fun getChildSimple(num: Int): NodeLink<K, T>? {
        return childs[num]
    }

    override fun get(key: K): INode<K, T>? {
        val comp = this.key.compareTo(key)
        return if (comp == 0) {
            this
        } else if (comp > 0 && childs[0] != null) {
            childs[0]?.node?.get(key)
        } else if (comp < 0 && childs[1] != null) {
            childs[1]?.node?.get(key)
        } else {
            null
        }
    }

    companion object {
        fun <K : Comparable<K>, T> height(node: Node<K, T>?): Int = node?.height ?: 0

        fun <K : Comparable<K>, T> height(nodeLink: NodeLink<K, T>?): Int =
            height(nodeLink?.node)

        fun <K : Comparable<K>, T> getBalance(node: Node<K, T>): Int =
            height(node.childs[0]) - height(node.childs[1])
    }
}

class AvlTree<K : Comparable<K>, T>(private val root: INode<K, T>?) {
    operator fun get(item: K): INode<K, T>? {
        return this.root?.get(item)
    }

    operator fun iterator(): AvlTreeIterator.IteratorBuilder<K, T> {
        return AvlTreeIterator.IteratorBuilder(this.root)
    }
}
