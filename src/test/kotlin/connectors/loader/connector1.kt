package connectors.loader

import configuration.Config
import connectors.Connector
import connectors.ConnectorDesc

class Connector1(config: Config) :
        Connector(config) {

    override fun run(input: Any?, output: (Any?) -> Unit) {
        output(input.toString())
    }
}