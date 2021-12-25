package connectors.io

import configuration.Config
import functions.FunctionConsumer
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.net.URL

internal class LocalFileConnectorTest {

    @Test
    fun run() {
        val resource: URL? = Thread.currentThread().contextClassLoader.getResource("./files")
        if (resource == null) {
            throw RuntimeException("file not exists")
        }
        val config : Config = Config.Builder()
            .add("root", resource.path)
            .add("pattern", "*.txt")
            .add("subFolder", "false")
            .build();

        val f: FunctionConsumer = LocalFileDescriptor.build(config)
        var content : String = ""
        val finput = {
            input : InputStream ->
            content = input.readAllBytes().decodeToString()
        }

        f.run(null) {
            output : Any? ->
            if (output is InputRecord) {
                output.consume(finput)
            }
        }

        print(content)
    }
}