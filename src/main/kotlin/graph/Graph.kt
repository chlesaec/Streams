package graph

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import java.util.*

open class Identifiable(val identifier : UUID = UUID.randomUUID())

@Serializable
class Node<T, U> internal constructor(val data : T,
                                      identifier : UUID = UUID.randomUUID()) : Identifiable(identifier) {
    val nexts = mutableListOf<Edge<T, U>>()
    val precs = mutableListOf<Edge<T, U>>()
}

@Serializable
class Edge<T, U> internal constructor(val data : U,
                 val start : Node<T, U>,
                 val end : Node<T, U>,
                 identifier : UUID = UUID.randomUUID()) : Identifiable(identifier) {
    init {
        start.nexts.add(this)
        end.precs.add(this)
    }
}

@Serializable
class Graph<T, U> internal constructor(val nodes : Map<UUID, Node<T,U>>,
                  val edges : Map<UUID, Edge<T,U>>) {
    // TODO : Add a function to walk into graph from sources nodes to sink nodes

    fun visit(start : Node<T, U>,
              selectNext : (nodeData : Node<T, U>,
                            next : Collection<Edge<T, U>>,
                            prec : Collection<Edge<T, U>>) -> Node<T, U>?) {
        var nextNode : Node<T, U>? = selectNext(start, start.nexts, start.precs)
        while (nextNode is Node<T, U>) {
            nextNode = selectNext(nextNode, nextNode.nexts, nextNode.precs)
        }
    }

    fun startNodes(): List<Node<T, U>> {
        val unstartedNode: Set<UUID> = this.edges.values.map { it.end.identifier }.toSet()
        return this.nodes.values.filter { !unstartedNode.contains(it.identifier) }
    }

    fun endNodes(): List<Node<T, U>> {
        return this.nodes.values.filter { it.nexts.isEmpty() }
    }

    fun <T1, U1> map(functionNode : (T) -> T1, functionEdge : (U) -> U1) : Graph<T1, U1> {
        val newNodes = nodes.map {
            val node : Node<T1, U1>  = Node(functionNode(it.value.data), it.key)
            it.key to node
        }.toMap()
        val newEdges = this.edges.map {
            val start : Node<T1, U1> = newNodes[it.value.start.identifier]!!
            val end : Node<T1, U1>  = newNodes[it.value.end.identifier]!!
            val node : Edge<T1, U1>  = Edge(functionEdge(it.value.data), start, end, it.key);
            it.key to node
        }.toMap()
        return Graph(newNodes, newEdges)
    }
}

@FunctionalInterface
interface UpdateGraphObserver<T, U> {
    fun updatedPredecessors(current: T, nexts: List<Pair<T, U>>) : Unit
}

class NodeBuilder<T, U> internal constructor(val g : GraphBuilder<T, U>,
                                             val data : T,
                                             val observer: UpdateGraphObserver<T, U>) : Identifiable() {
    val nexts : MutableList<EdgeBuilder<T, U>> = mutableListOf()

    fun addNext(next : NodeBuilder<T, U>, edgeData : U) : EdgeBuilder<T, U> {
        val e = EdgeBuilder<T, U>(edgeData, next)
        this.nexts.add(e)
        this.informObserver(next)
        return e
    }

    fun removeNext(identifier: UUID) {
        var index = 0
        var next: NodeBuilder<T, U>? = null
        while (index < this.nexts.size) {
            if (this.nexts[index].next.identifier == identifier) {
                next = this.nexts[index].next
                this.nexts.removeAt(index)
            }
            else {
                index++
            }
        }
        if (next != null) {
            this.informObserver(next)
        }
    }

    private fun informObserver(next : NodeBuilder<T, U>) {
        val predecessorData = next.findPredecessors().map {
            Pair(it.first.data, it.second.data)
        }
        this.observer.updatedPredecessors(this.data, predecessorData)
    }

    fun findPredecessors() : ImmutableList<Pair<NodeBuilder<T, U>, EdgeBuilder<T, U>>> {
        return this.g.edges()
            .filter { it.second.next == this }
            .toImmutableList()
    }

    fun build() : Node<T, U> {
        return Node<T, U>(this.data, this.identifier)
    }

    fun buildEdges(findNode : (UUID) -> Node<T, U>?) : List<Edge<T, U>> {
        return this.nexts.map {
            it.build(this, findNode)
        }
    }
}

class EdgeBuilder<T, U> internal constructor(val data : U, val next : NodeBuilder<T, U>) {

    val identifier = UUID.randomUUID()

    fun build(start : NodeBuilder<T, U>, nodeFinder : (UUID) -> Node<T, U>?) : Edge<T, U> {
        val startNode : Node<T, U> = nodeFinder(start.identifier) ?: start.build()
        val endNode  : Node<T, U> = nodeFinder(this.next.identifier) ?: this.next.build()
        val identifier = if (this.data is Identifiable) {
            this.data.identifier
        }
        else {
            UUID.randomUUID()
        }
        return Edge(this.data, startNode, endNode, identifier)
    }
}


class GraphBuilder<T, U>(val observer: UpdateGraphObserver<T, U>) {
    private val nodeBuilders = mutableListOf<NodeBuilder<T, U>>()

    // TODO:  Allow Run menu only if not empty & isConnected
    // TODO : detect cycles (to unallow it)

    fun addNode(data : T) : NodeBuilder<T, U> {
        val builder = NodeBuilder<T, U>(this, data, observer)
        this.nodeBuilders.add(builder)
        return builder
    }

    fun isEmpty() : Boolean = this.nodeBuilders.isEmpty()

    fun isConnected() : Boolean {
        if (this.isEmpty()) {
            return true
        }
        val visitedNode = mutableSetOf<UUID>()
        val currentNodes = ArrayDeque<NodeBuilder<T, U>>(5)
        currentNodes.add(this.nodeBuilders[0])
        visitedNode.add(this.nodeBuilders[0].identifier)
        while (currentNodes.isNotEmpty()) {
            val workingNode = currentNodes.removeFirst()
            workingNode.nexts
                .map( EdgeBuilder<T, U>::next )
                .forEach {
                    if (!visitedNode.contains(it.identifier)) {
                        currentNodes.add(it)
                        visitedNode.add(it.identifier)
                    }
                }
        }
        return visitedNode.size == this.nodeBuilders.size
    }

    fun removeNode(identifier: UUID) {
        this.nodeBuilders.forEach { it.removeNext(identifier) }
        var index = 0
        var found = false
        while (index < this.nodeBuilders.size && !found) {
            if (this.nodeBuilders[index].identifier == identifier) {
                this.nodeBuilders.removeAt(index)
                found = true
            }
            index++
        }
    }

    fun nodesBuilder() = this.nodeBuilders.toList()

    fun nodes() : List<T> = this.nodeBuilders.toList().map { it.data }

    fun edges() : List<Pair<NodeBuilder<T, U>, EdgeBuilder<T, U>>> {
        return this.nodeBuilders.flatMap { n: NodeBuilder<T, U> ->
            n.nexts.map { e : EdgeBuilder<T, U> -> Pair(n ,e) }
        }
    }

    fun removeEdge(startNode : NodeBuilder<T, U>, edge : EdgeBuilder<T, U>) {
        startNode.nexts.removeAll {
            it.identifier == edge.identifier
        }
    }

    fun build() : Graph<T, U> {
        val nodes : Map<UUID, Node<T, U>> = this.nodeBuilders.map { it.build() } //
            .map { it.identifier to it } //
            .toMap()
        val edges : Map<UUID, Edge<T, U>> = this.nodeBuilders.flatMap { it.buildEdges(nodes::get) } //
            .map{ it.identifier to it } //
            .toMap()
        return Graph<T, U>(nodes, edges)
    }

}
