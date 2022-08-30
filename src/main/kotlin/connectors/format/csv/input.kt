package connectors.format.csv

import configuration.Config
import connectors.*
import connectors.io.InputRecord
import functions.OutputFunction
import javafx.scene.image.Image
import job.JobConnectorData
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.InputStreamReader

val csvConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
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
        { findImage("csv.png") },
        { j: JobConnectorData, c : Config -> CSVReaderConnector(c) }
    )
{
    init {
        Connectors.register(this)
    }
}
class CSVReaderConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: OutputFunction) {
        if (input is InputRecord) {
            input.consume {
                CSVFormat.DEFAULT
                    .parse(InputStreamReader(it))
                    .forEach { output("main", it) }
            }
        }
    }
}
