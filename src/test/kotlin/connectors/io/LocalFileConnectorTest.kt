package connectors.io

import configuration.Config
import connectors.JobConfig
import functions.FunctionConsumer
import job.JobConnectorData
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

        val jcf = JobConnectorData(JobConfig(), LocalFileDescriptor, "n1", "id1")

        val f: FunctionConsumer = LocalFileDescriptor.build(jcf, config)
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
