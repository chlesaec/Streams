package connectors.loader

import configuration.Config
import connectors.Connector
import connectors.ConnectorDesc

class Connector1(config: Config, desc: ConnectorDesc) :
        Connector(config, desc) {

    override fun run(input: Any?, output: (Any?) -> Unit) {
        output(input.toString())
    }
}