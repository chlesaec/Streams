package job

import commons.Coordinate
import configuration.Config
import connectors.Connector
import connectors.ConnectorDesc
import connectors.JobConfig
import functions.FunctionConsumer
import graph.EdgeBuilder
import graph.Graph
import graph.GraphBuilder
import graph.NodeBuilder
import javafx.scene.image.Image
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
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



class LinkView(var color : Color,
               var width : Double) {
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
        cnx.initialize(config, this)
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

class JobLink(val view : LinkView, val filter: NextFilter) {
    fun onEvent(e: Event) {
        view.drawer?.updateCounter()
    }

    fun name() : String {
        return this.filter.names.joinToString(", ")
    }
}



class JobConnectorBuilder(val name : String,
                          val identifier : String,
                          val connectorDesc: ConnectorDesc,
                          var config : Config.Builder,
                          val view : ComponentView) {
    fun toJobConnector(job: JobConfig) : JobConnector {
        val connectorData = JobConnectorData(job, this.connectorDesc, this.name, this.identifier)
        return JobConnector(connectorData, this.config.build())
    }

    fun center() : Coordinate {
        val icon = this.connectorDesc.icon()
        return this.view.center(Coordinate(icon.width, icon.height))
    }

    fun inside(c : Coordinate) : Boolean {
        val icon : Image = this.connectorDesc.icon()
        val size = Coordinate(icon.width, icon.height)
        return this.view.position.x < c.x && this.view.position.x + size.x.toInt() > c.x
                && this.view.position.y < c.y && this.view.position.y + size.y.toInt() > c.y
    }


    fun show(graphicFunction : (Coordinate, Image) -> Unit) {
        graphicFunction(this.view.position, this.connectorDesc.icon())
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
