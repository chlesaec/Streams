package connectors.db

import com.zaxxer.hikari.HikariDataSource
import configuration.Config
import connectors.*
import connectors.generators.ClassGenerator
import connectors.generators.Parameter
import connectors.io.InputRecord
import connectors.io.LocalFileConnector
import javafx.scene.image.Image
import job.JobConnectorData
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.net.URLClassLoader
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredFunctions


val databaseConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("url", StringType())
            .add("username", StringType())
            .add("password", StringType())
            .add("table", StringType())
            .build()
    )
)

object DBDescriptor :
    ConnectorDesc(
        VersionedIdentifier("Database Input", Version(listOf(1))),
        Link(arrayOf()),
        InputRecord::class,
        databaseConfigDescription,
        { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./iconFiles.png").path) },
        { j: JobConnectorData, c : Config -> DatabaseInputConnector(c, DBDescriptor::buildObject) })
{
    init {
        Connectors.register(this)
    }

    fun buildDataSource(config: Config) : DataSource {
        val source = HikariDataSource()

        source.jdbcUrl = config.get("url")!!
        source.username = config.get("username")!!
        source.password = config.get("password")!!

        return source
    }

    fun buildClass(config: Config) : Any? {
        val dataSource = this.buildDataSource(config)
        val table: String = config.get("table")!!
        dataSource.connection.use {
            it.createStatement().executeQuery("SELECT * FROM ${table}")
                .use {
                    return this.buildClass(it, table)
                }
        }
    }

    fun buildClass(r : ResultSet, table: String) : Any? {
        val clazz = ClassGenerator("DBInputStruct${table}")
        val meta: ResultSetMetaData = r.metaData

        for (i : Int in 1..meta.columnCount) {
            val parameter = Parameter(meta.getColumnName(i), meta.getColumnClassName(i))
            clazz.addField(parameter)
        }




        return null
    }

    fun buildObject(c : Config, r : ResultSet) : Any? {
        return null
    }

}


class DatabaseInputConnector(config : Config,
                            val builder : (Config, ResultSet) -> Any?) : Connector(config) {

    private val source : DataSource

    private val tableName: String

    override fun run(input: Any?, output: (Any?) -> Unit) {
        this.source.connection.use {
            val query: PreparedStatement = it.prepareStatement("SELECT * FROM ${tableName}")
            query.use {
                val result: ResultSet = it.executeQuery()
                result.use {
                    while (result.next()) {
                        val item = this.builder(this.config, result)
                        if (item != null) {
                            output(item)
                        }
                    }
                }
            }
        }
    }

    init {
        source = HikariDataSource()

        source.jdbcUrl = config.get("url")!!
        source.username = config.get("username")!!
        source.password = config.get("password")!!

        this.tableName = config.get("table")!!
    }

}


class ObjectGenerator {
    fun generate() {
        val compilerArguments = K2JVMCompilerArguments();
        compilerArguments.freeArgs = listOf("/Users/christophe/Documents/archives/Dossiers/work/str/Streams/src/test/resources/generate/g1.kt")
        compilerArguments.destination = "/Users/christophe/Documents/archives/Dossiers/work/str/Streams/src/test/resources/generate/"
        compilerArguments.jvmTarget = "11"
        compilerArguments.classpath = System.getProperty("java.class.path")
        compilerArguments.noStdlib = true
        val messageCollector = SimpleKotlinCompilerMessageCollector()
        val exitCode: ExitCode = K2JVMCompiler().exec(
            messageCollector,
            Services.Builder().build(),
            compilerArguments);
        println("code ${exitCode.code}")
    }
}

fun main(args: Array<String>) {
    println(Class.forName("java.lang.String").kotlin.qualifiedName)

    ObjectGenerator().generate()
    val classLoader = ObjectGenerator::class.java.classLoader
    val loader = URLClassLoader(
        listOf(File("/Users/christophe/Documents/archives/Dossiers/work/str/Streams/src/test/resources/generate").toURI().toURL()).toTypedArray(),
        classLoader)

    val classType: Class<*> = loader.loadClass("connectors.db.generated.C1")
    val instance: Any = classType.kotlin.constructors.first().call("Hello")
    val companion = classType.kotlin.companionObjectInstance
    val kclass = companion?.javaClass?.kotlin
    val function = kclass?.declaredFunctions?.first()
    val instance2: Any? = function?.call(companion)
    println(instance.toString())
    println("v2 : ${instance2?.toString()}")
}



class SimpleKotlinCompilerMessageCollector : MessageCollector {
    override fun clear() {

    }

    override fun hasErrors(): Boolean {
        return false
    }

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        println("msg ${severity.name} : ${message}")
    }

}
