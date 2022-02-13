package connectors

import configuration.Config
import functions.FunctionConsumer
import javafx.scene.image.Image
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

class Link(val linkType : KClass<out Any>) {
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
    val intput: Link,
    val output: Link,
    val config: ConfigDescription,
    val icon : () -> Image,
    val builder : (Config) -> Connector
) {

    fun build(c: Config): FunctionConsumer {
        if (!this.config.isCompliant(c)) {
            throw IllegalStateException("config not compliant")
        }
        return this.builder(c)
    }

    fun canSucceed(prec : ConnectorDesc) {
        this.intput.canSucceed(prec.output)
    }
}


abstract class Connector(
    val config: Config
) : FunctionConsumer {

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
