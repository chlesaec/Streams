package connectors.loader

import configuration.Config
import connectors.Connector
import connectors.ConnectorDesc
import functions.OutputFunction

class Connector1(config: Config) :
        Connector(config) {

    override fun run(input: Any?, output: OutputFunction) {
        output("main", input.toString())
    }
}
