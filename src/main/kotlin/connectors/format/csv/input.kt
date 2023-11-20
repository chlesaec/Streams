package connectors.format.csv

import configuration.Config
import connectors.*
import connectors.generators.Generated
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
        findImage("csv.png"),
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
        else if (item.input != null && item.input::class.java.isAnnotationPresent(Generated::class.java)) {
            val csvLine = item.input.javaClass.fields
                .map { it.get(item.input) }
                .map { if (it == null) "" else it.toString() }
                .reduce { acc: String,
                          s: Any? ->
                    if (s == null) {
                        "${acc},"
                    }
                    else {
                        "${acc},${s}"
                    }
                }
            output("main", csvLine)
        }
    }
}

fun CSVRecord.header() : String {
    return this.parser.headerNames.reduce { acc: String,
                                             s: String ->
        "${acc},${s}"
    }
}

fun CSVRecord.line() : String {
    return this.reduce { acc: String,
                   s: String ->
        "${acc},${s}"
    }
}
