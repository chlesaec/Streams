package connectors

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class VersionTest {

    @Test
    fun testEquals() {
        val v1 = Version(listOf(1, 0))
        val v2 = Version(listOf(1, 0))
        val v3 = Version(listOf(1, 2))

        Assertions.assertEquals(v1, v2)
        Assertions.assertNotEquals(v1, v3)
    }
}
