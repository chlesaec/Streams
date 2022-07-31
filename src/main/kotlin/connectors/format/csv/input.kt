package connectors.format.csv

import configuration.Config
import connectors.*
import connectors.io.InputRecord
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
        Link(arrayOf(InputRecord::class)),
        CSVRecord::class,
        csvConfigDescription,
        { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./csv.png").path) },
        { j: JobConnectorData, c : Config -> CSVReaderConnector(c) }
    )
{
    init {
        Connectors.register(this)
    }
}
class CSVReaderConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        if (input is InputRecord) {
            input.consume {
                CSVFormat.DEFAULT
                    .parse(InputStreamReader(it))
                    .forEach { output(it) }
            }
        }
    }
}
