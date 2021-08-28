package connectors

import configuration.Config
import functions.FunctionConsumer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.IllegalStateException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
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

class Link(val linkType : KClass<Any>) {
    fun canSucceed(prec: Link): Boolean {
        return prec.linkType.isSubclassOf(this.linkType)
    }

    fun canPreced(prec: Link): Boolean = prec.canSucceed(this)
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

class Fields
private constructor(
        private val properties: Map<String, FieldType>
)  {
    class Builder() {
        private val properties = HashMap<String, FieldType>()

        fun add(name : String, prop : FieldType) {
            this.properties[name] = prop
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

class ConfigDescription(val description: ComposedType) {

    fun isCompliant(c : Config) : Boolean {
        return true
    }
}

data class Version(val v: IntArray)

data class VersionedIdentifier(
    val name: String,
    val version: Version
) {

}

open class ConnectorDesc(
    var identifier: VersionedIdentifier,
    val intput: Link,
    val output: Link,
    val config: ConfigDescription,
    val k: KClass<Connector>
) {

    fun build(c: Config): FunctionConsumer {
        if (!this.config.isCompliant(c)) {
            throw IllegalStateException("config not compliant")
        }
        val connectors = k.constructors.map { cs: KFunction<Connector> ->

            var cnx: Connector? = null
            val params: List<KParameter> = cs.parameters

            if (params.size == 2) {
                val p1: KParameter = params[0]
                val p2: KParameter = params[1]
                if (p1.type.classifier == Config::class &&
                    p2.type.classifier == ConnectorDesc::class
                ) {
                    val conn: Connector = cs.call(c, this)
                    cnx = conn as Connector
                }
            }

            cnx
        }
        if (!connectors.isEmpty()) {
            return connectors[0]!!
        }
        throw RuntimeException("no connectors")
    }

    fun canSucceed(prec : ConnectorDesc) {
        this.intput.canSucceed(prec.output)
    }
}


abstract class Connector(
    val config: Config,
    val desc: ConnectorDesc
) : FunctionConsumer {

}

class Connectors {
    private val connectors = HashMap<String, HashMap<Version, Connector>>()

    fun get(name: String, version: Version) :  Connector? {
        return this.connectors[name]?.get(version)
    }
}
