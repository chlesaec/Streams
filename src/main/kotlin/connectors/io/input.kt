package connectors.io

import configuration.Config
import connectors.*
import javafx.scene.image.Image
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

typealias InputStreamConsumer = (InputStream) -> Unit

data class InputRecord(val type : String,
                       val root : String,
                  val folder : String,
                  val objectName : String,
                  val consume : (inputFunction : InputStreamConsumer) -> Unit)

val localFileConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("root", StringType())
            .add("pattern", StringType())
            .add("subFolder", BooleanType())
            .build()
    )
)

object LocalFileDescriptor :
        ConnectorDesc(
            VersionedIdentifier("LocalFile", Version(listOf(1))),
            Link(arrayOf(Nothing::class)),
            InputRecord::class,
            localFileConfigDescription,
            { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./iconFiles.png").path) },
            { c : Config -> LocalFileConnector(c) })
         {
            init {
                Connectors.register(this)
            }
        }

class LocalFileConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        val root = config.get("root")
        val rootPath = Path.of(root)
        val pattern = config.get("pattern") ?: ""

        val dirStream = Files.newDirectoryStream(rootPath, pattern)
        dirStream.forEach {
            val record = InputRecord(
                "localIO",
                root ?: "",
                it.parent.toFile().path.substring((root ?: "").length),
                it.toFile().name) { consumer: InputStreamConsumer ->
                Files.newInputStream(it).use(consumer)
            }
            output(record)
        }
    }
}
