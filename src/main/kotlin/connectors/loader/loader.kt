package connectors.loader

import configuration.Config
import connectors.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import kotlin.jvm.internal.Reflection
import kotlin.reflect.*
import kotlin.reflect.full.createType


class ClassReference(
        val classpath: String,
        val jarGetter: Array<URL>
) {
    private val loader: ClassLoader = URLClassLoader.newInstance(
            this.jarGetter,
            this.javaClass.classLoader
    )

    private fun findWithConfig(constructor: KFunction<Connector>): Boolean {
        return if (constructor.parameters.size == 1) {
            val parameter: KParameter = constructor.parameters[0]
            Config::class.equals(parameter.type.classifier)
        } else {
            false
        }
    }

    fun <T : Any> find(className: String): KClass<T> {
        val loadClass: Class<*> = this.loader.loadClass(className)
        return Reflection.createKotlinClass(loadClass) as KClass<T>
    }

    fun find(): ((Config) -> Connector)? {
        val javaClass: Class<*>? = loader.loadClass(this.classpath)
        if (javaClass != null && javaClass.isAssignableFrom(Connector::class.java)) {
            val kotlinClass: KClass<Connector> =
                    Reflection.createKotlinClass(javaClass) as KClass<Connector>;
            val findFirst: Optional<KFunction<Connector>> = kotlinClass.constructors.stream()
                    .filter(this::findWithConfig)
                    .findFirst()
            if (findFirst.isPresent) {
                val construct: KFunction<Connector> = findFirst.get()
                return { cfg: Config -> construct.call(cfg) }
            }

        }
        return null
    }
}

/**
 * Config description
 * { "f1" : { "type" : "integer"},
 *   "f2" : { "type" : "object", "fields" : { ...}}
 *   }
 */
class ConfigDescriptionExtractor() {
    fun extract(jsonCfg: JsonObject): ConfigDescription {
        return ConfigDescription(this.extractComposedType(jsonCfg))
    }

    private fun extractComposedType(jsonCfg: JsonObject): ComposedType {
        val fieldsBuilder = Fields.Builder()
        jsonCfg.forEach { name: String, value: JsonElement ->
            if (value is JsonObject) {
                val vtype = value["type"]
                if (vtype is JsonPrimitive && vtype.content == "object") {
                    val fields = value["fields"]
                    if (fields is JsonObject) {
                        val extractedComposedType: ComposedType = this.extractComposedType(fields)
                        fieldsBuilder.add(name, extractedComposedType)
                    }
                } else if (vtype is JsonPrimitive && vtype.content == "integer") {
                    fieldsBuilder.add(name, IntType())
                } else if (vtype is JsonPrimitive && vtype.content == "string") {
                    fieldsBuilder.add(name, StringType())
                }else if (vtype is JsonPrimitive && vtype.content == "boolean") {
                    fieldsBuilder.add(name, BooleanType())
                }
            }
        }
        return ComposedType(fieldsBuilder.build())
    }

}

private fun extractConfig(jsonCfg: JsonObject): Config {
    val builder = Config.Builder()

    jsonCfg.forEach { k, v: JsonElement ->
        if (v is JsonObject) {
            val sub = extractConfig(jsonCfg)
            builder.addSub(k, sub)
        } else if (v is JsonPrimitive) {
            builder.add(k, v.content)
        }
    }
    return builder.build()
}


fun loadConnector(jsonDesc: JsonObject): ConnectorDesc {
    val cfg: JsonElement? = jsonDesc["config"]
    val configCnx: ConfigDescription = if (cfg is JsonObject) {
        ConfigDescriptionExtractor().extract(cfg)
    } else {
        ConfigDescription(ComposedType(Fields.Builder().build()))
    }

    val identifier = jsonDesc["identifier"]
    if (identifier !is JsonObject) {
        throw IllegalStateException("")
    }
    val name = identifier["name"]
    val versionArray = identifier["version"]
    if (name !is JsonPrimitive || versionArray !is JsonArray) {
        throw IllegalStateException("")
    }
    val versions = versionArray
            .map {
                if (it is JsonPrimitive) {
                    it.intOrNull
                } else {
                    null
                }
            }
            .filterNotNull()
            .toIntArray()
    val version = VersionedIdentifier(name.content, Version(versions))

    val connec = jsonDesc["connector"]
    if (connec !is JsonObject) {
        throw IllegalStateException("")
    }
    val cp = connec["classpath"]
    val paths = connec["paths"]

    if (cp !is JsonPrimitive || paths !is JsonArray) {
        throw IllegalStateException("")
    }
    val pathsFile: List<String> = paths.filterIsInstance(JsonPrimitive::class.java)
            .map(JsonPrimitive::content)
    val urls = pathsFile.map { fileName: String -> File(fileName).toURI().toURL() }
    val classReference = ClassReference(cp.content, urls.toTypedArray())

    val input = connec["input"]
    val outputCnx = connec["output"]
    if (input !is JsonPrimitive || outputCnx !is JsonPrimitive) {
        throw IllegalStateException("")
    }
    val inputType: KClass<Any> = classReference.find(input.content)
    val outputType: KClass<Any> = classReference.find(outputCnx.content)

    val processClassName = connec["processClass"]
    if (processClassName !is JsonPrimitive) {
        throw IllegalStateException("")
    }
    val kClassConn: KClass<Connector> = Connector::class
    val invariantInput = KTypeProjection.invariant(inputType.createType())
    val invariantOutput = KTypeProjection.invariant(outputType.createType())

    val createType: KType = kClassConn.createType()

    val processClass: KClass<Connector> = classReference.find<Connector>(processClassName.content)

    return ConnectorDesc(
            identifier = version,
            intput = Link(inputType),
            output = Link(outputType),
            config = configCnx,
            processClass
    )

}