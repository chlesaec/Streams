package connectors.logRow

import configuration.Config
import connectors.*
import connectors.commons.RowError
import functions.InputItem

import functions.OutputFunction
import javafx.scene.image.Image
import job.JobConnectorData
import mu.KLogger
import mu.KotlinLogging
import java.util.logging.SimpleFormatter

val LogConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("config", StringType())
            .build()
    )
)

object LogRowDescriptor :
    ConnectorDesc(
        VersionedIdentifier("Log Row", Version(listOf(1))),
        LinkInput(arrayOf(Any::class)),
        LinkOutput(),
        LogConfigDescription,
        { findImage("iconFiles.png") },
        { j: JobConnectorData, c : Config -> LogRowConnector(c) })
{
    init {
        Connectors.register(this)
    }
}

class LogRowConnector(config : Config) : Connector(config) {

    val logger : KLogger

    init {
        logger = KotlinLogging.logger {}
        // TODO : init logger from "config" file
       // val formatter = SimpleFormatter()
    }

    override fun run(item: InputItem, output: OutputFunction) {
        val input = item.input;
        if (input is RowError) {
            input.log(this.logger)
        }
        else if (input != null) {
            this.logger.info(input.toString())
        }
    }
}
