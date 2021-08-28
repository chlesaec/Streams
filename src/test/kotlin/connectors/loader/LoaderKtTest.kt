package connectors.loader

import configuration.Config
import configuration.Referential
import connectors.ConfigDescription
import connectors.ConnectorDesc
import connectors.FieldType
import connectors.SimpleType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URL
import java.nio.file.Files
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class LoaderKtTest {

    @Test
    fun classReference() {
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
    fun configDesc() {
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
    fun classLoaderTest() {

        val classLoader = this.javaClass.classLoader
        val classLoader1 = Int::class.java.classLoader
        val x : String
        val loadClass = classLoader.loadClass("java.lang.String")

    }

    @Test
    fun loadConnector() {
        val resource: URL? = Thread.currentThread().contextClassLoader.getResource("./loader/connector1.json")
        if (resource == null) {
            throw RuntimeException("file not exists")
        }
        val content : String = resource.readText()

        val jsonCfg: JsonElement = Json.parseToJsonElement(content)
        val loadConnector: ConnectorDesc = connectors.loader.loadConnector(jsonCfg.jsonObject)
        Assertions.assertEquals("testCNX", loadConnector.identifier.name)

        val config = Config.Builder().build()
        val build = loadConnector.build(config)

        Assertions.assertTrue(build is Connector1);
    }
}