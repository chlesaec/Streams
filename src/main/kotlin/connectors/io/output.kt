package connectors.io

import configuration.Config
import connectors.*
import javafx.scene.image.Image
import job.JobConnectorData
import java.io.*
import java.nio.file.Files
import java.nio.file.Path

interface ByteReader {
    fun read() : Iterator<ByteArray>
}
interface ByteReaderGetter {
    fun reader() : ByteReader
}

val localFileOutputConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("path", StringType())
            .build()
    )
)

object LocalFileOutputDescriptor :
    ConnectorDesc(
        VersionedIdentifier("Local File Output", Version(listOf(1))),
        Link(arrayOf(ByteReader::class, InputRecord::class)),
        Nothing::class,
        localFileOutputConfigDescription,
        { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./icon1.png").path) },
        { j: JobConnectorData, c : Config -> LocalFileOutputConnector(c) }
    ) {
    init {
        Connectors.register(this)
    }
}

class LocalFileOutputConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        if (input is ByteReader) {
            val file = Path.of(config.get("path"))

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
            val root = Path.of(config.get("path"))
            val destPath = File(root.toFile(), input.folder);
            if (!destPath.exists()) {
                Files.createDirectories(destPath.toPath())
            }
            val destFile = File(destPath, input.objectName)
            input.consume {
                this.copyStream(it, destFile)
            }
        }
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
