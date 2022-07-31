package connectors.db

import com.zaxxer.hikari.HikariDataSource
import configuration.Config
import org.h2.tools.Server
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class DBDescriptorTest {

    @Test
    fun buildClass() {
        val server = Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
        println(server.service.url)
        println(server.service.name)

        Class.forName("org.h2.Driver")

        val source = HikariDataSource()

        source.jdbcUrl = "jdbc:h2:mem://localhost:9092/~/db" //"jdbc:h2:mem:test:9092"
        source.username = "sa"
        source.password = ""

        val cnx = source.connection
        cnx.use {
            it.createStatement()
                .execute("CREATE TABLE T1 (C1  VARCHAR(20), C2 INT)")
        }

        val config = Config.Builder()
            .add("url","jdbc:h2:mem://localhost:9092/~/db")
            .add("username", "sa")
            .add("password", "")
            .add("table", "T1")
            .build()

        val cls = DBDescriptor.buildClass(config)

        server.stop()
    }
}
