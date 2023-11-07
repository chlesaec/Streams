package connectors.loader

import commons.Coordinate
import configuration.Config
import connectors.*
import graph.GraphBuilder
import graph.NodeBuilder
import javafx.scene.paint.Color
import job.*
import kotlinx.serialization.json.*
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.cast


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

class JobLoader {
    fun loadJob(jsonDesc: JsonObject): JobBuilder {
        val graph = GraphBuilder<JobConnectorBuilder, JobLink>(JobGraphObserver)
        val connectors = jsonDesc["connectors"]
        if (connectors !is JsonArray) {
            throw RuntimeException("No connectors in job")
        }
        val connectorLoader = ConnectorBuilderLoader()
        val cbuilder: List<NodeBuilder<JobConnectorBuilder, JobLink>> = connectors
            .filter { it is JsonObject }
            .map { connectorLoader.loadConnectorBuilder(it.jsonObject) }
            .filterNotNull()
            .map { graph.addNode(it) }
            .toList()
        val links = jsonDesc["links"]
        if (links !is JsonArray) {
            throw RuntimeException("No links in job")
        }
        links.forEach {
            this.addEdge(cbuilder, it)
        }
        return JobBuilder(graph)
    }

    private fun addEdge(cbuilder: List<NodeBuilder<JobConnectorBuilder, JobLink>>,
                        jsonEdge : JsonElement) {
        if (jsonEdge is JsonObject) {
            val from = jsonEdge["from"]
            val to = jsonEdge["to"]
            val filter = jsonEdge["filter"]
            if (from is JsonPrimitive && from.isString
                && to is JsonPrimitive && to.isString
                && filter is JsonArray) {
                val connectorFrom : NodeBuilder<JobConnectorBuilder, JobLink>? = cbuilder.find { it.data.identifier == from.content }
                val connectorTo : NodeBuilder<JobConnectorBuilder, JobLink>? = cbuilder.find { it.data.identifier == to.content }
                if (connectorFrom is NodeBuilder<JobConnectorBuilder, JobLink>
                    && connectorTo is NodeBuilder<JobConnectorBuilder, JobLink>) {
                    val content = filter.filter { it is JsonPrimitive && it.isString }
                            .map(JsonPrimitive::class::cast)
                            .map(JsonPrimitive::content)
                            .toTypedArray()
                    connectorFrom.addNext(connectorTo, JobLink(LinkView(Color.BLACK, 3.0),
                        JobLinkData(NextFilter(content))))
                }
            }
        }
    }
}

class ConnectorBuilderLoader() {

    fun loadConnectorBuilder(jsonDesc: JsonObject): JobConnectorBuilder? {
        val cfg: JsonElement? = jsonDesc["config"]
        val config: Config.Builder? = if (cfg is JsonObject) {
            extractConfig(cfg)
        } else {
            null
        }
        val jobConnector = loadConnector(jsonDesc)
        if (config == null || jobConnector == null) {
            return null
        }

        val view = loadView(jsonDesc) ?: ComponentView(Coordinate(0.0, 0.0))

        val name: JsonElement? = jsonDesc["name"]
        val identifier: JsonElement? = jsonDesc["identifier"]
        if (name is JsonPrimitive && name.isString
            && identifier is JsonPrimitive && identifier.isString) {
            return JobConnectorBuilder(name.content, identifier.content, jobConnector, config, view)
        }
        return null
    }

    private fun extractConfig(jsonCfg: JsonObject): Config.Builder {
        val builder = Config.Builder()

        jsonCfg.forEach { k, v: JsonElement ->
            if (v is JsonObject) {
                val sub = extractConfig(v)
                builder.addSub(k, sub)
            } else if (v is JsonPrimitive) {
                builder.add(k, v.content)
            }
        }
        return builder
    }

    private fun loadView(viewDesc: JsonObject): ComponentView? {
        val pos = viewDesc["position"]
        val position: Coordinate = if (pos is JsonObject) {
            val x: Double = pos["x"]?.jsonPrimitive?.double ?: 0.0
            val y: Double = pos["y"]?.jsonPrimitive?.double ?: 0.0
            Coordinate(x, y)
        } else {
            Coordinate(0.0, 0.0)
        }

        return ComponentView(position)
    }

    private fun loadConnector(jsonDesc: JsonObject): ConnectorDesc? {
        val identifier = jsonDesc["connector"]
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
            .toList()

        return Connectors.get(name.content, Version(versions))
    }

}

class JobSaver() {
    fun saveJob(job : JobBuilder)  : JsonObject {

        // connectors
        val elements = HashMap<String, JsonElement>()
        val connectorSaver = ConnectorBuilderSaver()
        val connectorsJson = JsonArray(
            job.graph.nodes().map {
                connectorSaver.saveConnectorBuilder(it)
            }
        )
        elements["connectors"] = connectorsJson

        // edges
        val edges: List<JsonObject> = job.graph.edges().map {
            val obj = HashMap<String, JsonElement>()
            obj.put("from", JsonPrimitive(it.first.data.identifier))
            obj.put("to", JsonPrimitive(it.second.next.data.identifier))
            obj.put("filter", JsonArray(it.second.data.data.filter.names.map { JsonPrimitive(it) }.toList()))
            JsonObject(obj)
        }
        elements["links"] = JsonArray(edges)

        return JsonObject(elements)
    }

}

class ConnectorBuilderSaver() {

    fun saveConnectorBuilder(jobConncector : JobConnectorBuilder) : JsonObject {
        val connectorElements = HashMap<String, JsonElement>()
        val cfg : JsonObject = this.saveConfig(jobConncector.config)
        connectorElements["config"] = cfg

        saveConnector(jobConncector.connectorDesc, connectorElements)

        connectorElements["name"] = JsonPrimitive(jobConncector.name)
        connectorElements["identifier"] = JsonPrimitive(jobConncector.identifier)

        this.saveView(jobConncector.view, connectorElements)

        return JsonObject(connectorElements)
    }

    private fun saveConfig(config : Config.Builder) : JsonObject {
        val elements = HashMap<String, JsonElement>()
        val propFunction = { name: String , value: String -> elements[name] = JsonPrimitive(value) }
        config.forProperties(propFunction)

        val subFunction = { name: String , sub: Config.Builder -> elements[name] = this.saveConfig(sub) }
        config.forSubreferentials(subFunction)
        return JsonObject(elements)
    }

    private fun saveView(view : ComponentView, elements : MutableMap<String, JsonElement>) {
        val jsonPos = HashMap<String, JsonElement>()
        jsonPos["x"] = JsonPrimitive(view.position.x)
        jsonPos["y"] = JsonPrimitive(view.position.y)
        elements["position"] = JsonObject(jsonPos)
    }

    private fun saveConnector(desc : ConnectorDesc, elements : MutableMap<String, JsonElement>) {
        val subElements = HashMap<String, JsonElement>()
        subElements["name"] = JsonPrimitive(desc.identifier.name)
        subElements["version"] = JsonArray(desc.identifier.version.v.map { s : Int -> JsonPrimitive(s) })
        elements["connector"] = JsonObject(subElements)
    }

}
