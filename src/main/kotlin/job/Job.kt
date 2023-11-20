package job

import commons.Coordinate
import configuration.Config
import connectors.Connector
import connectors.ConnectorDesc
import connectors.JobConfig
import connectors.Neighbour
import graph.*
import runner.Runner


class ComponentView(var position : Coordinate = Coordinate(0.0, 0.0)) {
    fun center(size : Coordinate) : Coordinate {
        return this.position + (size / 2.0)
    }
}

interface LinkDrawer {
    fun draw()

    fun updateCounter()
}

data class Color(val r: Int, val g: Int, val b: Int)


class LinkView(var color : Color = Color(0, 0, 0),
               var width : Double = 3.0) {
    var drawer : LinkDrawer? = null

    var count : Long = 0

    fun onEvent(e: Event) {
        if (e is ItemEvent) {
            count++
            if (drawer != null) {

            }
        }
    }
}

class JobConnectorData(val jobConfig: JobConfig,
                       val connectorDesc: ConnectorDesc,
                       val name: String,
                       val identifier: String) {
    fun buildConnector(config : Config) : Connector {
        val cnx = this.connectorDesc.build(this, config)
        cnx.initialize(this)
        return cnx
    }
}

class JobConnector(val connectorData: JobConnectorData,
                   val config : Config) {
    fun buildConnector() : Connector {
        return this.connectorData.buildConnector(this.config)
    }
}

sealed interface Event
object ItemEvent : Event
object EndEvent : Event

class NextFilter(val names : Array<String>) {

    constructor(s: String) : this(Array(1) { s }) {
    }

    fun select(name: String) : Boolean {
        return name == "*" || names.contains("*") || names.contains(name)
    }
}

class JobLinkData(val filter: NextFilter,
    var endName: String = "*") {
    fun name() : String {
        return this.filter.names.joinToString(", ")
    }
}

class JobLink(val view : LinkView, val data: JobLinkData) {
    fun onEvent(e: Event) {
        view.drawer?.updateCounter()
    }

    fun name() : String {
        return this.data.name()
    }
}

/**
 * Define a connector builder for job.
 */
class JobConnectorBuilder(var name : String,
                          val identifier : String,
                          val connectorDesc: ConnectorDesc,
                          var config : Config.Builder,
                          val view : ComponentView) {
    fun toJobConnector(job: JobConfig) : JobConnector {
        val connectorData = JobConnectorData(job, this.connectorDesc, this.name, this.identifier)
        return JobConnector(connectorData, this.config.build())
    }

}

object JobGraphObserver: UpdateGraphObserver<JobConnectorBuilder, JobLink> {

    override fun updatedPredecessors(current: JobConnectorBuilder,
                                     nexts: List<Pair<JobConnectorBuilder, JobLink>>) {
        val observer = current.connectorDesc.config.observer
        if (observer != null) {
            val neighbours: List<Neighbour> = nexts.map {
                Neighbour(it.first.connectorDesc, it.first.name, it.second.name())
            }
            observer.onPredecessorUpdated(neighbours)
        }
    }
}

typealias JobGraphBuilder = GraphBuilder<JobConnectorBuilder, JobLink>
typealias JobNodeBuilder = NodeBuilder<JobConnectorBuilder, JobLink>
typealias JobEdgeBuilder = EdgeBuilder<JobConnectorBuilder, JobLink>

class JobBuilder(val graph : JobGraphBuilder) {

    val jobConfig = JobConfig()

    fun build() : Job {
        val jobGraph : Graph<JobConnector, JobLink> = this.graph
            .build()
            .map({ j: JobConnectorBuilder -> j.toJobConnector(jobConfig) })
        { link: JobLink -> link }
        return Job(jobGraph)
    }
}

class Job(val graph : Graph<JobConnector, JobLink>) {

    fun run(runner : Runner) {
        val exec = runner.compile(this)
        exec()
    }
}
