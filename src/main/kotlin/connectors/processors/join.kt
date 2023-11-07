package connectors.processors

import configuration.Config
import connectors.*
import connectors.io.InputRecord
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.InputStreamReader

val joinConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("root", StringType())
            .add("pattern", StringType())
            .add("subFolder", BooleanType())
            .build()
    )
)

object JoinDescriptor:
        ConnectorDesc(
            VersionedIdentifier("Join", Version(listOf(1))),
            LinkInput(arrayOf(CSVRecord::class)),
            LinkOutput().add("main", CSVRecord::class),
            joinConfigDescription,
            { findImage("join.png") },
            { j: JobConnectorData, c : Config -> JoinConnector(c) }
        )
{
    init {
        Connectors.register(this)
    }
}


class JoinConnector(config : Config) : Connector(config) {
    override fun run(item: InputItem, output: OutputFunction) {

    }
}
