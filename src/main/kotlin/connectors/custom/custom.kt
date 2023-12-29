package connectors.custom

import configuration.Config
import connectors.*
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import java.io.File
import java.net.URLClassLoader


typealias CustomFunction = (input: Any, output: (Any) -> Unit) -> Unit


val customConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("jar path", StringType())
            .add("class name", StringType())
            .add("function name", StringType())
            .build()
    )
)

object CustomDescriptor :
    ConnectorDesc(
        VersionedIdentifier("Custom processor", Version(listOf(1))),
        LinkInput(arrayOf(Any::class)),
        LinkOutput().add("main", Any::class)
            .add("error", Exception::class),
        customConfigDescription,
        findImage("custom.png"),
        { j: JobConnectorData, c: Config -> CustomConnector(c, j) }) {
    init {
        Connectors.register(this)
    }
}


class CustomConnector(
    config: Config,
    val j: JobConnectorData
) : Connector(config) {

    val customFunction: CustomFunction = this.extractCustom()

    override fun run(input: InputItem, output: OutputFunction) {
        val item = input.input
        if (item is Any) {
            try {
                this.customFunction(item) { result: Any ->
                    output("main", result)
                }
            } catch (ex: Exception) {
                output("error", ex)
            }
        }
    }

    private fun extractCustom(): CustomFunction {
        val jarPath: String? = this.config.get("jar path")
        if (jarPath is String) {
            val loader = URLClassLoader(
                "custom",
                arrayOf(File(jarPath).toURI().toURL()),
                Thread.currentThread().contextClassLoader
            )

            val className: String? = this.config.get("class name")
            val functionName: String? = this.config.get("function name")
            if (className is String && functionName is String) {
                try {
                    val clazz: Class<*> = loader.loadClass(className)
                    val declaredMethod = clazz.getDeclaredMethod(
                        functionName,
                        Any::class.java,
                        kotlin.jvm.functions.Function1::class.java
                    )
                    return { item: Any, output: (Any) -> Unit -> declaredMethod.invoke(null, item, output) }
                } catch (ex: ClassNotFoundException) {

                }
            }
        }


        return { input: Any, output: (Any) -> Unit ->
            if (input != null) {
                output(input)
            }
        }
    }
}
