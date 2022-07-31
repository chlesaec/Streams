package connectors.generators

import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream

internal class ClassGeneratorTest {

    @Test
    fun generateCode() {
        val out = ByteArrayOutputStream()

        val generator = ClassGenerator("C1")
        generator.addField(Parameter("f1", "String"))
        generator.addField(Parameter("f2", "Int"))
        generator.addField(Parameter("f3", "Boolean"))
        generator.generateCode(out)

        println(out.toString())
    }
}
