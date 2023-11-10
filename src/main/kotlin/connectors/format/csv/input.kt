package connectors.format.csv

import configuration.Config
import connectors.*
import connectors.io.InputRecord
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.InputStreamReader

val csvConfigDescription = ConfigDescription(
    ComposedType(Fields.Builder()
            .add("separator", StringType())
            .build()
    )
)
object CsvReaderDescriptor :
    ConnectorDesc(
        VersionedIdentifier("CSVReader", Version(listOf(1))),
        LinkInput(arrayOf(InputRecord::class)),
        LinkOutput().add("main", CSVRecord::class),
        csvConfigDescription,
        { findImage("csv_old.png") },
        { j: JobConnectorData, c : Config -> CSVReaderConnector(c) }
    )
{
    init {
        Connectors.register(this)
    }
}
class CSVReaderConnector(config : Config) : Connector(config) {
    override fun run(item: InputItem, output: OutputFunction) {
        if (item.input is InputRecord) {
            item.input.consume {
                CSVFormat.DEFAULT
                    .parse(InputStreamReader(it))
                    .forEach { output("main", it) }
            }
        }
    }
}
