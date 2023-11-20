package connectors.db

import com.zaxxer.hikari.HikariDataSource
import configuration.Config
import connectors.*
import connectors.generators.*
import connectors.io.InputRecord
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import javax.sql.DataSource
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredFunctions


val databaseConfigDescription = ConfigDescription(
    ComposedType(Fields.Builder()
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
        LinkInput(arrayOf()),
        LinkOutput().add("main", InputRecord::class),
        databaseConfigDescription,
        findImage("iconFiles.png"),
        { j: JobConnectorData, c : Config -> DatabaseInputConnector(c, j) })
{
    init {
        Connectors.register(this)
    }
}


class DatabaseInputConnector(config : Config,
                            val j: JobConnectorData) : Connector(config) {

    private val source : DataSource

    private val tableName: String

    private var mainClazz : ClassGenerator? = null

    private var objectBuilder : (ResultSet) -> Any? = { null }

    override fun initialize(j: JobConnectorData) {
        val className = this.createClass(j, config)

        val classType: Class<*> = j.jobConfig.loadClass(className)
        val companion = classType.kotlin.companionObjectInstance
        val kclass = companion?.javaClass?.kotlin
        val function = kclass?.declaredFunctions?.first()

        this.objectBuilder = { r : ResultSet -> function?.call(companion, r) }
    }


    override fun run(input: InputItem, output: OutputFunction) {
        this.source.connection.use {
            val query: PreparedStatement = it.prepareStatement("SELECT * FROM ${tableName}")
            query.use {
                val result: ResultSet = it.executeQuery()
                result.use {
                    while (result.next()) {
                        val item = this.buildObject(result)
                        if (item != null) {
                            output("main", item)
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

    fun buildDataSource(config: Config) : DataSource {
        val source = HikariDataSource()

        source.jdbcUrl = config.get("url")!!
        source.username = config.get("username")!!
        source.password = config.get("password")!!

        return source
    }

    private fun createClass(j: JobConnectorData, config: Config) : String {
        val src : SourceFileGenerator = this.buildSource(j, config)
        val srcFile = src.generate(j.jobConfig.rootFolder.toFile())
        j.jobConfig.addSource(srcFile)
        return "${src.packageName}.${src.classes().firstOrNull()?.name}"
    }

    private fun buildSource(j: JobConnectorData, config: Config) : SourceFileGenerator {
        val dataSource = this.buildDataSource(config)
        val table: String = config.get("table")!!
        val sf = SourceFileGenerator("dbinput.${j.name}", "${j.name}.kt")

        dataSource.connection.use {
            it.createStatement().executeQuery("SELECT * FROM ${table}")
                .use {
                    sf.addClass(this.buildClass(it, j.name, table))
                }
        }
        return sf
    }

    private fun buildClass(r : ResultSet, name: String, table: String) : ClassGenerator {
        val clazz = ClassGenerator("${name}${table}")
        val meta: ResultSetMetaData = r.metaData

        val companion = CompanionBuilderGenerator()
        val m = MethodGenerator("build")
        m.addInputParam(Parameter("result", "java.sql.ResultSet"))
        m.returnType = "${name}${table}"

        for (i : Int in 1..meta.columnCount) {
            val parameter = Parameter(meta.getColumnName(i), this.toKotlinClass(meta.getColumnClassName(i)))
            clazz.addField(parameter)
            this.linesForResult(m, parameter, i)
        }
        val inputParam = clazz.fields().map { it.name }.joinToString(", ")
        m.addLine("return ${clazz.name}(${inputParam})")
        companion.addMethod(m)

        clazz.builder = companion

        this.mainClazz = clazz
        return clazz
    }

    private fun toKotlinClass(javaClass: String) : String {
        if (javaClass == "java.lang.String") {
            return "String"
        }
        if (javaClass == "java.lang.Integer") {
            return "Int"
        }
        return javaClass
    }

    private fun linesForResult(m : MethodGenerator, p : Parameter, index : Int) {
        if (p.type == "String") {
            m.addLine("val ${p.name} : String? = result.getString(${index})")
        }
        else if (p.type == "Int") {
            m.addLine("val ${p.name}Tmp = result.getInt(${index})")
            m.addLine("val ${p.name} : Int?")
            m.addLine("if (result.wasNull()) {");
            m.addLine("\t${p.name} = null");
            m.addLine("}");
            m.addLine("else {");
            m.addLine("\t${p.name} = ${p.name}Tmp");
            m.addLine("}");
        }

    }

    private fun buildObject(r : ResultSet) : Any? {
        val instance2: Any? = this.objectBuilder(r)
        return instance2
    }
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
