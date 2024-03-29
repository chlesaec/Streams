package connectors.db

import com.zaxxer.hikari.HikariDataSource
import configuration.Config
import connectors.Connector
import connectors.JobConfig
import functions.InputItem
import job.JobConnectorData
import job.JobLinkData
import job.NextFilter
import org.h2.tools.Server
import org.junit.jupiter.api.Test

import java.nio.file.Path

internal class DBDescriptorTest {

    @Test
    fun testBuildClass() {
        val server = Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();


        Class.forName("org.h2.Driver")

        val source = HikariDataSource()

        source.jdbcUrl = "jdbc:h2:mem://localhost:9092/~/db" //"jdbc:h2:mem:test:9092"
        source.username = "sa"
        source.password = ""

        val cnx = source.connection
        cnx.use {
            it.createStatement()
                .execute("CREATE TABLE T1 (C1  VARCHAR(20), C2 INT)")
            it.createStatement().execute("INSERT INTO T1 (C1, C2) values ('HELLO', 4)")
        }

        val config = Config.Builder()
            .add("url","jdbc:h2:mem://localhost:9092/~/db")
            .add("username", "sa")
            .add("password", "")
            .add("table", "T1")
            .build()
        val jobCfg = JobConfig()
        jobCfg.rootFolder = Path.of(
            Thread.currentThread().contextClassLoader.getResource(".").toURI() )
        val jcd = JobConnectorData(jobCfg, DBDescriptor, "C1", "id1")
        val ld = JobLinkData(NextFilter("*"))
        val connector: Connector = DBDescriptor.build(jcd, config)
        connector.initialize(jcd)
        connector.run(InputItem(jcd,ld, null)) { branch: String, item: Any? ->
            if (item != null) {
                println(item.toString())
            }
        }

       // val cls = DBDescriptor.buildClass(config)

        server.stop()
    }
}
