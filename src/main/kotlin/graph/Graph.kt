package graph

import kotlinx.serialization.Serializable
import java.util.*

open class Identifiable(val identifier : UUID = UUID.randomUUID())

@Serializable
class Node<T, U> internal constructor(val data : T, identifier : UUID = UUID.randomUUID()) : Identifiable(identifier) {
    val nexts = mutableListOf<Edge<T, U>>()
    val precs = mutableListOf<Edge<T, U>>()
}

@Serializable
class Edge<T, U> internal constructor(val data : U,
                 val start : Node<T, U>,
                 val end : Node<T, U>) : Identifiable() {
    init {
        start.nexts.add(this)
        end.precs.add(this)
    }
}

@Serializable
class Graph<T, U> internal constructor(val nodes : Map<UUID, Node<T,U>>,
                  val edges : Map<UUID, Edge<T,U>>) {

}

class NodeBuilder<T, U> internal constructor(val g : GraphBuilder<T, U>, val data : T) : Identifiable() {
    val nexts : MutableList<EdgeBuilder<T, U>> = mutableListOf<EdgeBuilder<T, U>>()

    fun addNext(dataNext : T, edgeData : U) : EdgeBuilder<T, U> {
        val nextNode : NodeBuilder<T, U> = g.addNode(dataNext)
        return this.addNext(nextNode, edgeData)
    }

    fun addNext(next : NodeBuilder<T, U>, edgeData : U) : EdgeBuilder<T, U> {
        val e = EdgeBuilder<T, U>(edgeData, next);
        this.nexts.add(e)
        return e
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

    fun build(start : NodeBuilder<T, U>, nodeFinder : (UUID) -> Node<T, U>?) : Edge<T, U> {
        val startNode : Node<T, U> = nodeFinder(start.identifier) ?: start.build()
        val endNode  : Node<T, U> = nodeFinder(this.next.identifier) ?: this.next.build()
        return Edge(this.data, startNode, endNode)
    }
}

class GraphBuilder<T, U>() {
    private val nodeBuilders = mutableListOf<NodeBuilder<T, U>>()

    fun addNode(data : T) : NodeBuilder<T, U> {
        val builder = NodeBuilder<T, U>(this, data)
        this.nodeBuilders.add(builder)
        return builder
    }

    fun build() : Graph<T, U> {
        val nodes : Map<UUID, Node<T, U>> = this.nodeBuilders.map { it.build() } //
            .map { it.identifier to it } //
            .toMap()
        val edges : Map<UUID, Edge<T, U>> = this.nodeBuilders.flatMap { it.buildEdges(nodes::get) } //
            .map{ it.identifier to it } //
            .toMap();
        return Graph<T, U>(nodes, edges);
    }

}