package connectors

import configuration.Config
import connectors.db.SimpleKotlinCompilerMessageCollector
import functions.FunctionConsumer
import javafx.scene.image.Image
import job.JobConnectorData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*

import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

sealed class Result {
    abstract fun thenCheck(next: () -> Result): Result
}

object ResultOK : Result() {
    override fun thenCheck(next: () -> Result): Result {
        return next()
    }
}

class Error(val reason: String) : Result() {
    override fun thenCheck(next: () -> Result): Result {
        return this
    }
}

class LinkInput(val linkTypes : Array<KClass<out Any>>) {

    fun canSucceed(precClazz: LinkOutput): Array<String> {
        return this.linkTypes.map {
            val ret = this.canSucceedClass(it, precClazz)
            println("Succeed before reduce : ${ret.size}")
            ret
        }
            .reduce{
                l: Array<String>, r : Array<String> ->
                println("redure : ${l.size}")
                Array(l.size + r.size) {
                if (it < l.size) {
                    l[it]
                }
                else {
                    r[it - l.size]
                }
            }
            }
           /*
            .reduce {
                l: Array<String>, r : Array<String> -> Array(l.size + r.size) {
                    if (it < l.size) {
                        l[it]
                    }
                    else {
                        r[it - l.size]
                    }
                }
            }*/
    }

    private fun canSucceedClass(succ: KClass<out Any>,
                                prec: LinkOutput): Array<String> {
        return prec.selectCompatibleOutput(succ)
    }
}

class LinkOutput() {
    private val outputs = mutableMapOf<String, KClass<out Any>>()

    fun add(name: String, clazz: KClass<out Any>) : LinkOutput {
        this.outputs[name] = clazz
        return this
    }

    fun selectCompatibleOutput(succ: KClass<out Any>) : Array<String> {
        return outputs.entries
            .filter { this.canSucceedClass(it.value, succ) }
            .map { it.key }
            .toTypedArray()
    }

    private fun canSucceedClass(prec: KClass<out Any>,
                                succ: KClass<out Any>): Boolean {
        println("Prec ${prec.simpleName} is sub class of ${succ.simpleName} ${prec.isSubclassOf(succ)}")
        return prec.isSubclassOf(succ)
    }
}

interface Constraint<T> {
    fun valid(value: T): Result
}

sealed interface FieldType {
    fun valid(value: JsonElement) : Boolean

    fun valid(value: String) : Boolean

    fun valid(value: Config) : Boolean
}
class ComposedType(val fields: Fields) : FieldType {
    override fun valid(value: JsonElement): Boolean {
        if (value !is JsonObject) {
            return false
        }
        return true
    }

    override fun valid(value: String) : Boolean {
        return false
    }

    override fun valid(value: Config) : Boolean {
        return this.fields.fields().all {
            val content : String? = value.get(it.first)
            if (content is String) {
                return it.second.valid(content)
            }
            val sub : Config? = value.sub(it.first)
            if (sub is Config) {
                return it.second.valid(sub)
            }
            return false;
        }
    }
}

abstract class SimpleType : FieldType {

    override fun valid(c : Config) : Boolean {
        return false
    }

    override fun valid(value: JsonElement): Boolean {
        return value is JsonPrimitive && this.valid(value.content)
    }
}

object EmptyType: FieldType {
    override fun valid(value: JsonElement): Boolean = false

    override fun valid(value: String): Boolean = false

    override fun valid(value: Config): Boolean = false
}

class Fields
private constructor(
        private val properties: Map<String, FieldType>
)  {
    class Builder() {
        private val properties = HashMap<String, FieldType>()

        fun add(name : String, prop : FieldType) : Builder {
            this.properties[name] = prop
            return this
        }

        fun build(): Fields {
            return Fields(
                HashMap(this.properties)
            )
        }
    }

    fun field(name : String) : FieldType? {
        return this.properties[name]
    }

    fun fields() : List<Pair<String, FieldType>> {
        return this.properties.map {
            Pair(it.key, it.value)
        }
    }
}

class IntType() : SimpleType() {
    override fun valid(value: String): Boolean {
        try {
            value.toLong()
            return true
        } catch (ex: NumberFormatException) {
            return false
        }
    }
}

class StringType() : SimpleType() {
    override fun valid(value: String): Boolean {
        return true
    }
}

class BooleanType() : SimpleType() {
    override fun valid(value: String): Boolean {
        return "true".equals(value.trim(), true)
                || "false".equals(value.trim(), true)
    }
}

// TODO : Add type "LocalFileType", "RealNumberType" at least

class ConfigDescription(val description: ComposedType) {

    fun isCompliant(c : Config) : Boolean {
        return true
    }
}

data class Version(val v: List<Int>) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        var res : Int = 0
        var index : Int = 0
        while (res == 0 && index < this.v.size && index < other.v.size) {
            res = if (this.v[index] > other.v[index]) {
                1
            }
            else if (this.v[index] < other.v[index]) {
                -1
            }
            else 0
            index++
        }
        if (res == 0 && index < this.v.size) {
            res = 1
        }
        else if (res == 0 && index < other.v.size) {
            res = -1
        }
        return res
    }

}

data class VersionedIdentifier(
    val name: String,
    val version: Version
) {

}

open class ConnectorDesc(
    var identifier: VersionedIdentifier,
    val intput: LinkInput,
    val output: LinkOutput,
    val config: ConfigDescription,
    val icon : () -> Image,
    val builder : (JobConnectorData, Config) -> Connector
) {

    fun build(j: JobConnectorData, c: Config): Connector {
        if (!this.config.isCompliant(c)) {
            throw IllegalStateException("config not compliant")
        }
        return this.builder(j, c)
    }

}

class JobConfig() {
    val classLoader: ClassLoader by lazy {
        this.sources.forEach(this::compile)
        val urlClasses : Array<URL> = Array(1) {
            this.targetForlder().toURI().toURL()
        }
        URLClassLoader(urlClasses, JobConfig::class.java.classLoader)
    }

    private val sources = mutableListOf<File>()

    var rootFolder: Path = Path.of(".")

    fun addSource(source: File) {
        this.sources.add(source)
    }

    fun loadClass(name: String) : Class<*> {
        return this.classLoader.loadClass(name)
    }

    private fun compile(source: File) : ExitCode {
        val compilerArguments = K2JVMCompilerArguments();
        compilerArguments.freeArgs = listOf(source.path)
        compilerArguments.destination = targetForlder().path
        compilerArguments.jvmTarget = "11"
        compilerArguments.classpath = System.getProperty("java.class.path")
        compilerArguments.noStdlib = true
        val messageCollector = SimpleKotlinCompilerMessageCollector()
        val exitCode: ExitCode = K2JVMCompiler().exec(
            messageCollector,
            Services.Builder().build(),
            compilerArguments)
        return exitCode
    }

    private fun targetForlder() : File =
        File(rootFolder.toFile(),  "/generate")

}


abstract class Connector(
    val config: Config
) : FunctionConsumer {

    open fun initialize(config: Config, j: JobConnectorData) {}
}

object Connectors {
    private val connectors = HashMap<String, SortedMap<Version, ConnectorDesc>>()

    fun get(name: String, version: Version): ConnectorDesc? {
        return this.connectors[name]?.get(version)
    }

    fun get(name: String): ConnectorDesc? {
        val cnx = this.connectors[name]
        if (cnx is SortedMap) {
            return cnx[cnx.lastKey()]
        }
        return null
    }

    fun names() : Collection<String> = this.connectors.keys

    fun register(connectorDesc: ConnectorDesc) {
        val name : String = connectorDesc.identifier.name
        val version : Version = connectorDesc.identifier.version
        this.connectors.compute(name)
        { _: String, m: SortedMap<Version, ConnectorDesc>? ->
            val map: SortedMap<Version, ConnectorDesc> =
                if (m == null) TreeMap()
                else m
            map[version] = connectorDesc
            map
        }
    }
}
