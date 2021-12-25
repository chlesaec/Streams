package connectors.io

import configuration.Config
import connectors.*
import javafx.scene.image.Image
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path

interface ByteReader {
    fun read(data : ByteArray) : Int
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
        VersionedIdentifier("LocalFileOutput", Version(listOf(1))),
        Link(ByteReader::class),
        Link(Nothing::class),
        localFileOutputConfigDescription,
        { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./icon1.png")) },
        { c : Config -> LocalFileOutputConnector(c) }
    ) {
    init {
        Connectors.register(this)
    }
}

class LocalFileOutputConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        if (input is ByteReader) {
            val file = Path.of(config.get("root"))

            Files.newBufferedWriter(file)
                .use() {
                    writer : BufferedWriter ->
                    val data = ByteArray(1024)
                    var nbe = input.read(data)
                    while (nbe > 0) {
                        writer.write(data.decodeToString())
                        nbe = input.read(data)
                    }
                }
        }
    }
}
