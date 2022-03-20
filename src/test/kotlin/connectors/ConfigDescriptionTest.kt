package connectors

import configuration.Config
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class ConfigDescriptionTest {

    @Test
    fun getDescription() {
        val description = ConfigDescription(
            ComposedType(
                Fields.Builder()
                    .add("root", StringType())
                    .add("pattern", StringType())
                    .add("subFolder", BooleanType())
                    .build()
            )
        )
        val cfg = Config.Builder()
            .add("root", "rv")
            .add("pattern", "pat")
            .add("subFolder", "true")
            .build()
        Assertions.assertTrue(description.isCompliant(cfg))

    }
}
