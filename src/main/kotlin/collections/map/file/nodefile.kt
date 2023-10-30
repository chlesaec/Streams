package collections.map.file

import collections.map.Node
import collections.map.NodeBuilder
import collections.map.NodeLink
import java.io.RandomAccessFile

interface Serializer<T> {
    fun serialize(item: T): ByteArray
    fun deserialize(data: ByteArray): T
}

class NodeFile<K : Comparable<K>, T>(
    /** file where tree is stored  */
    private val file: RandomAccessFile,
    private val keySerializer: Serializer<K>,
    private val dataSerializer: Serializer<T>
) {
    fun findRoot(): Node<K, T>? {
        synchronized(file) {
            if (file.length() == 0L) {
                return null
            }
            file.seek("Tree".length.toLong())
            val startRoot = file.readLong()
            val link: NodeLinkFile<K, T> = NodeLinkFile(this, startRoot)
            return link.node
        }
    }

    /**
     * Replace current root on file.
     *
     * @param root : new root.
     */
    fun newRoot(root: Node<K, T>) {
        synchronized(file) {
            val link: NodeLinkFile<K, T> = root.link as NodeLinkFile<K, T>
            val start: Long = link.startPos
            file.seek("Tree".length.toLong())
            file.writeLong(start)
        }
    }

    /**
     * Read node from file.
     *
     * @param nodeLink : link to this node.
     * @return
     */
    fun getNode(nodeLink: NodeLinkFile<K, T>): Node<K, T> {
        synchronized(file) {
            file.seek(nodeLink.startPos)
            val height = file.readInt()
            val child1 = file.readLong()
            val child2 = file.readLong()
            val keySize = file.readInt()
            val dataSize = file.readInt()
            val key = read(keySize, keySerializer)
            val startData = file.filePointer
            val dataGetter =
                { readFrom(startData, dataSize, dataSerializer) }
            val n: Node<K, T> = Node(nodeLink, key, dataGetter)
            if (child1 > 0) {
                n.setChildSimple(0, NodeLinkFile(this, child1))
            }
            if (child2 > 0) {
                n.setChildSimple(1, NodeLinkFile(this, child2))
            }
            n.height = height
            return n
        }
    }

    /**
     * Add new node on file.
     *
     * @param key : node key.
     * @param data : node data.
     * @return the new node.
     */
    fun createNode(key: K, data: T): Node<K, T> {
        synchronized(file) {
            if (file.length() == 0L) {
                file.writeChars("Tree")
                file.writeLong(file.filePointer + java.lang.Long.BYTES)
            }
            val startPos = file.length()
            file.seek(startPos)
            file.writeInt(1) // height
            file.writeLong(0L) // left child
            file.writeLong(0L) // right child
            val keyBytes = keySerializer.serialize(key)
            val dataBytes = dataSerializer.serialize(data)
            file.writeInt(keyBytes.size)
            file.writeInt(dataBytes.size)
            file.write(keyBytes)
            file.write(dataBytes)
            val link: NodeLinkFile<K, T> = NodeLinkFile(this, startPos)
            return Node(link, key) { data }
        }
    }

    /**
     * Save an existing node when its childs were updated.
     *
     * @param node : changed node to save.
     * @param startPos : pos of node in file.
     */
    fun saveNode(node: Node<K, T>, startPos: Long) {
        synchronized(file) {
            file.seek(startPos)
            file.writeInt(node.height)
            saveLink(node, 0)
            saveLink(node, 1)
        }
    }

    private fun saveLink(node: Node<K, T>, numLink: Int) {
        val link: NodeLink<K, T>? = node.getChildSimple(numLink)
        if (link is NodeLinkFile) {
            file.writeLong(link.startPos)
        } else {
            file.writeLong(0L)
        }
    }

    private fun <U> readFrom(start: Long, size: Int, serializer: Serializer<U>): U {
        synchronized(file) {
            file.seek(start)
            return read(size, serializer)
        }
    }

    private fun <U> read(size: Int, serializer: Serializer<U>): U {
        val data = ByteArray(size)
        file.read(data)
        return serializer.deserialize(data)
    }
}

class NodeLinkFile<K : Comparable<K>, T>(
    private val file: NodeFile<K, T>,
    val startPos: Long
) :
    NodeLink<K, T> {

    override var node: Node<K, T>
        get() = file.getNode(this)
        set(value) = this.file.saveNode(value, startPos)
}

class BuilderFile<K : Comparable<K>, T>(private val file: NodeFile<K, T>) : NodeBuilder<K, T> {
    override fun findRoot(): Node<K, T>? {
        return file.findRoot()
    }

    override fun newRoot(root: Node<K, T>) {
        file.newRoot(root)
    }

    override fun build(key: K, data: () -> T): Node<K, T> {
        return file.createNode(key, data())
    }
}
