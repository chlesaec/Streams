package collections.map

import collections.map.memory.MemoryNode

interface NodeBuilder<K : Comparable<K>, T> {
    fun findRoot(): Node<K, T>?
    fun newRoot(root: Node<K, T>)
    fun build(key: K, data: () -> T): Node<K, T>
}

class AvlTreeBuilder<K : Comparable<K>, T>(val builder: NodeBuilder<K, T>) {

    private var root: Node<K, T>? = this.builder.findRoot()



    /**
     * See https://www.programiz.com/dsa/avl-tree
     * A child become the parent.
     * Used to maintain balanced tree.
     *
     * @param parent : current parent.
     * @param start : child that will be new parent.
     * @return new Parent.
     */
    private fun rotate(parent: Node<K, T>, start: Int): Node<K, T>? {
        // get futur parent.
        val pivot = parent.getChild(start)
        if (pivot == null) {
            return null
        }

        // get futur direct child of "current parent"
        val subChild = pivot.getChild(1 - start)
        pivot.setChild(1 - start, null)
        parent.setChild(start, null)

        // new parent own its ancient parent as child.
        pivot.setChild(1 - start, parent)
        // ancient parent put its grand child as direct child.
        parent.setChild(start, subChild)
        return pivot
    }

    private fun rebalance(node: Node<K, T>, firstChild: Int, item: K): Node<K, T>? {
        val child = node.getChild(firstChild)
        val childData: K? = child?.key
        if (child != null && childData != null) {
            val comp = item.compareTo(childData)
            val ret: Node<K, T>? =
                if ((comp < 0 && firstChild == 0)
                || (comp > 0 && firstChild == 1)) {
                rotate(node, firstChild)
            } else {
                val newLeftChild = rotate(child, 1 - firstChild)
                node.setChild(firstChild, newLeftChild)
                rotate(node, firstChild)
            }
            return ret
        }
        return null;
    }

    private fun insertNode(from: Node<K, T>?, newNode: Node<K, T>): Node<K, T>? {
        if (from == null) {
            return newNode
        }
        val nodeItem: K = from.key
        val value: Int = newNode.key.compareTo(nodeItem)
        if (value < 0) {
            val newLeft = this.insertNode(from.getChild(0), newNode)
            from.setChild(0, newLeft)
        } else {
            val newRight = this.insertNode(from.getChild(1), newNode)
            from.setChild(1, newRight)
        }
        val balanceFactor = Node.getBalance(from)
        if (balanceFactor > 1) {
            return rebalance(from, 0, newNode.key)
        } else if (balanceFactor < -1) {
            return rebalance(from, 1, newNode.key)
        }
        return from
    }

    // Insert a node
    fun insertNode(key: K, data: () -> T) {
        // Find the position and insert the node
        val newNode: Node<K, T> = builder.build(key, data)
        if (this.root == null) {
            this.root = newNode
        } else {
            val newRoot: Node<K, T>? = this.insertNode(this.root, newNode)
            if (newRoot !== this.root && newRoot != null) {
                this.root = newRoot
                builder.newRoot(newRoot)
            }
        }
    }

    fun build(): AvlTree<K, T> {
        return this.build(10)
    }

    /**
     * build immutable AVL Tree to be used for search key/value.
     *
     * @param limit : depth on this tree where we keep node in memory.
     * (total of node keep in memory will be 2^limit).
     * @return immutable AVL Tree.
     */
    fun build(limit: Int): AvlTree<K, T> {
        // even if tree is mainly stored on disk, keep elements in memory
        // to accelerate reading (deep of 10, roughly 1000 elements)
        val r = this.root
        if (r == null) {
            return AvlTree(null) // no element
        }
        val rootNode: INode<K, T> = MemoryNode(r, 1, limit)
        return AvlTree(rootNode)
    }

    private fun checkNode(node: Node<K, T>): String {
        val builder = StringBuilder()
        val current: K = node.key
        val h: Int = Node.height(node)
        val left = node.getChild(0)
        if (left != null) {
            val leftKey: K = left.key
            if (leftKey!!.compareTo(current) > 0) {
                builder.append(System.lineSeparator() + "Error Left > Parent")
            }
            builder.append(checkNode(left))
        }
        val right = node.getChild(1)
        if (right != null) {
            val rightKey: K = right.key
            if (rightKey.compareTo(current) < 0) {
                builder.append(System.lineSeparator() + "Error Right(" + rightKey + ") < Parent (" + current + ")")
            }
            builder.append(checkNode(right))
        }
        if (h != Math.max(Node.height(left), Node.height(right)) + 1) {
            builder.append(
                System.lineSeparator() + "Height Error ==> " + current + " h=" + h + " left: " + Node.height(left) + ", right: " + Node.height(
                    right
                )
            )
        }
        val bal = Node.getBalance(node)
        if (bal < -1 || bal > 1) {
            builder.append(System.lineSeparator() + "Balance error " + bal + " node " + node.key)
        }
        return builder.toString()
    }

    /**
     * Method for unit test only (package protected)
     * to check builder is balanced.
     */
    fun check() {
        val r = this.root
        if (r != null) {
            val errors = checkNode(r)
            if (errors != null && !errors.isEmpty()) {
                throw RuntimeException(errors)
            }
        }
    }
}
