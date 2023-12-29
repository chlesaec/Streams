package connectors.custom

import configuration.Config
import connectors.Connector
import connectors.JobConfig
import functions.InputItem
import job.JobConnectorData
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class CustomConnectorTest {


    @Test
    fun testCustom() {
        val jarContainer = Thread.currentThread().contextClassLoader.getResource("./jar/customFunction.jar")

        val config = Config.Builder()
            .add("jar path",jarContainer.path)
            .add("class name", "custom.CustomKt")
            .add("function name", "treatItem")
            .build()


        val jobCfg = JobConfig()
        jobCfg.rootFolder = Path.of(
            Thread.currentThread().contextClassLoader.getResource(".").toURI() )

        val jcd = JobConnectorData(jobCfg, CustomDescriptor, "C1", "id1")
        val connector: Connector = CustomDescriptor.build(jcd, config)
        connector.initialize(jcd)
        var index = 0
        connector.run(InputItem(jcd, null, 3))  { branch: String, item: Any? ->
            Assertions.assertTrue(item is Int)
            Assertions.assertEquals("main", branch)
            if (index == 0) {
                Assertions.assertEquals(4, item)
            }
            else {
                Assertions.assertEquals(6, item)
            }
            index++
        }
    }


}
