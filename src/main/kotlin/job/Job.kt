package job

import configuration.Config
import connectors.ConnectorDesc
import functions.FunctionConsumer
import graph.EdgeBuilder
import graph.Graph
import graph.GraphBuilder
import graph.NodeBuilder
import kotlinx.serialization.Serializable
import runner.Runner
import ui.ComponentView
import ui.Coordinate
import ui.LinkView


@Serializable
class JobConnector(val connectorDesc: ConnectorDesc,
                   val config : Config,
                   val view : ComponentView) {
    fun buildConnector() : FunctionConsumer {
        return this.connectorDesc.build(this.config)
    }

    fun center() : Coordinate {
        val icon = this.connectorDesc.icon()
        return this.view.center(Coordinate(icon.width, icon.height))
    }
}

class JobLink(val view : LinkView)

class JobConnectorBuilder(val name : String,
                          val identifier : String,
                          val connectorDesc: ConnectorDesc,
                          var config : Config.Builder,
                          val view : ComponentView) {
    fun toJobConnector() : JobConnector = JobConnector(this.connectorDesc, this.config.build(), this.view)

    fun center() : Coordinate {
        val icon = this.connectorDesc.icon()
        return this.view.center(Coordinate(icon.width, icon.height))
    }
}

typealias JobGraphBuilder = GraphBuilder<JobConnectorBuilder, JobLink>
typealias JobNodeBuilder = NodeBuilder<JobConnectorBuilder, JobLink>
typealias JobEdgeBuilder = EdgeBuilder<JobConnectorBuilder, JobLink>

class JobBuilder(val graph : JobGraphBuilder) {
    fun build() : Job {
        val jobGraph : Graph<JobConnector, JobLink> = this.graph.build().map(JobConnectorBuilder::toJobConnector) { link: JobLink -> link }
        return Job(jobGraph)
    }
}

class Job(val graph : Graph<JobConnector, JobLink>) {

    fun run(runner : Runner) {
        val exec = runner.compile(this)
        exec()
    }
}
