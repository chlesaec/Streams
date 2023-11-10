package connectors.io

import configuration.Config
import connectors.*
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import org.apache.commons.csv.CSVRecord
import java.io.*
import java.nio.file.Files
import java.nio.file.Path

interface ByteReader {
    fun read() : Iterator<ByteArray>
}


val localFileOutputConfigDescription = ConfigDescription(
    ComposedType(Fields.Builder()
            .add("path", StringType())
            .build()
    )
)

object LocalFileOutputDescriptor :
    ConnectorDesc(
        VersionedIdentifier("Local File Output", Version(listOf(1))),
        LinkInput(arrayOf(ByteReader::class, InputRecord::class, CSVRecord::class)),
        LinkOutput(),
        localFileOutputConfigDescription,
        { findImage("icon1.png") },
        { j: JobConnectorData, c : Config -> LocalFileOutputConnector(c) }
    ) {
    init {
        Connectors.register(this)
    }
}

class LocalFileOutputConnector(config : Config) : Connector(config) {

    var outputFile: OutputStream? = null

    var counter = 0

    override fun run(item: InputItem, output: OutputFunction) {
        val input = item.input;
        val file = Path.of(config.get("path"))
        if (input is ByteReader) {
            Files.newBufferedWriter(file)
                .use() {
                    writer : BufferedWriter ->
                    val data : Iterator<ByteArray> = input.read()
                    while (data.hasNext()) {
                        val array = data.next()
                        writer.write(array.decodeToString())
                    }
                }
        }
        else if (input is InputRecord) {
            val destPath = File(file.toFile(), input.folder);
            if (!destPath.exists()) {
                Files.createDirectories(destPath.toPath())
            }
            val destFile = File(destPath, input.objectName)
            input.consume {
                this.copyStream(it, destFile)
            }
        }
        else if (input is CSVRecord) {
            val out = this.getOutputStream()
            if (counter == 0) {
                val title = input.parser.headerNames.reduce { acc: String,
                                                              s: String ->
                    "${acc},${s}"
                }
                out.write(title.toByteArray())
                out.write(System.lineSeparator().toByteArray())
            }
            this.counter++
            val data = input.reduce { acc: String,
                                      s: String ->
                "${acc},${s}"
            }
            out.write(data.toByteArray())
            out.write(System.lineSeparator().toByteArray())
            if (counter >= 40) {
                out.flush()
                counter = 1
            }
        }
    }

    private fun getOutputStream() : OutputStream {
        var out = this.outputFile
        if (out == null) {
            val file = Path.of(config.get("path"))
            val fileDest = file.toFile()
            if (!fileDest.exists()) {
                fileDest.createNewFile()
            }
            out = FileOutputStream(fileDest)
            this.outputFile = out
        }
        return out
    }

    override fun end() {
        super.end()
        this.outputFile?.close()
        this.outputFile = null
    }

    private fun copyStream(input : InputStream,
                           outFile : File) {
        if (!outFile.exists()) {
            outFile.createNewFile()
        }
        FileOutputStream(outFile).use {
            input.transferTo(it)
        }
    }
}
