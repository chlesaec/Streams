package graph

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class GraphBuilderTest {

    @Test
    fun testGraphBuilder() {
        val builder = GraphBuilder<String, String>()
        Assertions.assertTrue(builder.isEmpty())
        Assertions.assertTrue(builder.isConnected())

        val n1 = builder.addNode("N1")
        Assertions.assertFalse(builder.isEmpty())
        Assertions.assertTrue(builder.isConnected())

        val n2 = builder.addNode("N2")
        Assertions.assertFalse(builder.isEmpty())
        Assertions.assertFalse(builder.isConnected())

        val n3 = builder.addNode("N3")
        val n4 = builder.addNode("N4")
        val n5 = builder.addNode("N5")

        // unconnected, miss n5
        n1.addNext(n2, "n1n2")
        n2.addNext(n3, "n2n3")
        n2.addNext(n4, "n2n4")
        Assertions.assertFalse(builder.isEmpty())
        Assertions.assertFalse(builder.isConnected())

        // unconnected with cycle, miss n5
        n4.addNext(n1, "n4n1")
        Assertions.assertFalse(builder.isEmpty())
        Assertions.assertFalse(builder.isConnected())

        // connected with cycle
        n4.addNext(n5, "n4n5")
        Assertions.assertFalse(builder.isEmpty())
        Assertions.assertTrue(builder.isConnected())
    }
}
