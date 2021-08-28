package job

import connectors.Connector
import functions.FunctionConsumer
import graph.Graph

class ConnectorLink(val f : FunctionConsumer?)

class Job(val graph : Graph<Connector, ConnectorLink>) {


    fun run() {
        
    }

}