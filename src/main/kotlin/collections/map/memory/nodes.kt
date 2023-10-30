package collections.map.memory

import collections.map.INode
import collections.map.Node
import collections.map.NodeBuilder
import collections.map.NodeLink

class NodeLinkMemory<K : Comparable<K>, T>(
    key: K,
    value: () -> T
) : NodeLink<K, T> {

    override var node: Node<K, T> = Node(this, key, value)
}

class MemoryNode<K : Comparable<K>, T>(
    private val delegate: INode<K, T>,
    deep: Int,
    limit: Int
) :
    INode<K, T> {

    private val childs: Array<INode<K, T>?> = arrayOfNulls(2)

    override val key: K = this.delegate.key

    override val data: () -> T = {
        if (dataValue == null) {
            dataValue = delegate.data()
        }
        dataValue!!
    }

    private var dataValue: T? = null

    init {
        val delegateLeft: INode<K, T>? = delegate.getChild(0)
        val delegateRight: INode<K, T>? = delegate.getChild(1)
        if (deep < limit) {
            if (delegateLeft != null) {
                childs[0] = MemoryNode(delegateLeft, deep + 1, limit)
            } else {
                childs[0] = null
            }
            if (delegateRight != null) {
                childs[1] = MemoryNode(delegateRight, deep + 1, limit)
            } else {
                childs[1] = null
            }
        } else {
            childs[0] = delegateLeft
            childs[1] = delegateRight
        }
    }

    override fun getChild(num: Int): INode<K, T>? {
        return childs[num]
    }

    override operator fun get(key: K): INode<K, T>? {
        val comp = this.key.compareTo(key)
        return if (comp == 0) {
            this
        } else if (comp > 0) {
            childs[0]?.get(key)
        } else {
            childs[1]?.get(key)
        }
    }
}

class BuilderMemo<K : Comparable<K>, T> : NodeBuilder<K, T> {

    private var root: Node<K, T>? = null

    override fun findRoot(): Node<K, T>? {
        return this.root
    }

    override fun newRoot(root: Node<K, T>) {
        this.root = root
    }

    override fun build(key: K, data: () -> T): Node<K, T> {
        val link: NodeLink<K, T> = NodeLinkMemory(key, data)
        return link.node
    }
}
