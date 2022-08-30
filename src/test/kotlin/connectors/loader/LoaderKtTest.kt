package connectors.loader

import configuration.Config
import connectors.*
import functions.OutputFunction
import javafx.scene.image.Image
import job.JobBuilder
import job.JobConnectorBuilder
import job.JobConnectorData
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class LoaderKtTest {

    @Test
    fun testClassReference() {
        val resource = Thread.currentThread()
                .contextClassLoader
                .getResource("./jar/commons-lang3-3.8.1.jar")
        val cf = ClassReference("common",
           Array(1) {i : Int -> resource})
        val classe : KClass<Any> = cf.find<Any>("org.apache.commons.lang3.Conversion")
        Assertions.assertEquals("Conversion", classe.simpleName)
        val instance = classe.createInstance()
        Assertions.assertNotNull(instance)
    }

    @Test
    fun testConfigDesc() {
        val jsonCfg: JsonElement = Json.parseToJsonElement(
            "{" +
                    "\"f1\": { \"type\": \"integer\" }," +
                    "\"f2\": { \"type\": \"object\"," +
                    "          \"fields\": { \"sub\": {\"type\" : \"string\"} } " +
                    "}" +
                    "}"
        )
        Assertions.assertTrue(jsonCfg is JsonObject)
        if (jsonCfg is JsonObject) {
            val config: ConfigDescription = ConfigDescriptionExtractor().extract(jsonCfg)

            val fields: List<Pair<String, FieldType>> = config.description.fields.fields();
            Assertions.assertEquals(2, fields.size);
            val f1: FieldType? = config.description.fields.field("f1");
            Assertions.assertTrue(f1 is SimpleType)
            if (f1 is SimpleType) {
                Assertions.assertTrue(f1.valid("324"))
                Assertions.assertFalse(f1.valid("Hello"))
            }
        }
    }

    @Test
    fun testClassLoader() {

        val classLoader = this.javaClass.classLoader
        val classLoader1 = Int::class.java.classLoader
        val x : String
        val loadClass = classLoader.loadClass("java.lang.String")

    }

    init {
      val desc = ConnectorDesc(
          VersionedIdentifier("testCNX", Version(listOf(1, 0))),
          LinkInput(arrayOf(Int::class)),
          LinkOutput().add("main", Int::class),
          ConfigDescription(ComposedType(Fields.Builder().build())),
          { findImage("icon1.png") }
      ) { j: JobConnectorData, c: Config -> FakeConnector(c) }
      Connectors.register(desc)
    }

    @Test
    fun testLoadJob() {
        val resource: URL? = Thread.currentThread().contextClassLoader.getResource("./loader/job1.json")
        if (resource == null) {
            throw RuntimeException("file not exists")
        }
        val content : String = resource.readText()

        val jsonJob: JsonElement = Json.parseToJsonElement(content)
        val loader = JobLoader()
        val builder : JobBuilder = loader.loadJob(jsonJob.jsonObject)
        Assertions.assertEquals(3, builder.graph.nodes().size)

        val job = builder.build()
        Assertions.assertEquals(2, job.graph.edges.size)

        val saver = JobSaver()
        val json = saver.saveJob(builder)
        Assertions.assertNotNull(json.jsonObject["links"])

        val builderCopy = loader.loadJob(json)
        Assertions.assertEquals(3, builderCopy.graph.nodes().size)
    }

    @Test
    fun testLoadConnector() {
        val resource: URL? = Thread.currentThread().contextClassLoader.getResource("./loader/connector1.json")
        if (resource != null) {
            val content : String = resource.readText()

            val jsonJob: JsonElement = Json.parseToJsonElement(content)
            val loader = ConnectorBuilderLoader()

            val jobBuilder = loader.loadConnectorBuilder(jsonJob.jsonObject)

            Assertions.assertTrue(jobBuilder is JobConnectorBuilder)
            Assertions.assertEquals("testCnx1", jobBuilder?.name)
            Assertions.assertEquals("1", jobBuilder?.identifier)

            if (jobBuilder is JobConnectorBuilder) {
                val saver = ConnectorBuilderSaver()
                val json = saver.saveConnectorBuilder(jobBuilder)
                Assertions.assertEquals("testCnx1", json["name"]?.jsonPrimitive?.content)
            }
            return
        }
        throw RuntimeException("file not exists")
    }
}

class FakeConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: OutputFunction) {
        output("*", input)
    }
}
