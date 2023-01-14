package connectors.loader

import configuration.Config
import connectors.Connector
import connectors.ConnectorDesc
import functions.InputItem
import functions.OutputFunction

class Connector1(config: Config) :
        Connector(config) {

    override fun run(input: InputItem, output: OutputFunction) {
        output("main", input.input.toString())
    }
}
