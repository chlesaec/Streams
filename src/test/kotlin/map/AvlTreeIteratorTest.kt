package collections.map

import collections.map.memory.BuilderMemo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AvlTreeIteratorTest {

    @Test
    operator fun next() {
        val builderMemo: BuilderMemo<Int, String> = BuilderMemo()
        val treeBuilder: AvlTreeBuilder<Int, String> = AvlTreeBuilder(builderMemo)

        for (i in 0..29) {
            treeBuilder.insertNode(i) { "Hello_$i" }
            treeBuilder.insertNode(400 - i) { "Hello_" + (400 - i) }
        }

        val tree = treeBuilder.build()
        val iterator: Iterator<INode<Int, String>> = tree.iterator().build()
        this.testIterator(iterator, 0, 400, 60)

        val iterator2: Iterator<INode<Int, String>> = tree.iterator().startAt(200).build()
        this.testIterator(iterator2, 371, 400, 30)

        val iterator3: Iterator<INode<Int, String>> = tree.iterator().endAt(200).build()
        this.testIterator(iterator3, 0, 29, 30)

        val iterator4: Iterator<INode<Int, String>> = tree.iterator().endAt(380).startAt(20).build()
        this.testIterator(iterator4, 20, 380, 20)

        val iterator5: Iterator<INode<Int, String>> = tree.iterator().startAt(380).endAt(20).build()
        this.testIterator(iterator5, 0, 0, 0)
    }

    private fun testIterator(iterator: Iterator<INode<Int, String>>, min: Int, max: Int, nbeElement: Int) {
        var nbe = 0
        var next: INode<Int, String>? = null
        val oldKey: Int? = null
        while (iterator.hasNext()) {
            next = iterator.next()
            assertNotNull(next)
            if (oldKey != null) {
                assertTrue(oldKey < next.key)
            }
            if (nbe == 0) {
                assertEquals(min, next.key)
            }
            nbe++
        }
        assertEquals(nbeElement, nbe)
        if (nbe > 0) {
            assertEquals(max, next?.key)
        }
    }
}