package collections.map

import collections.map.file.*
import collections.map.memory.BuilderMemo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.io.RandomAccessFile

class AvlTreeTest {
    @Test
    fun memoryTest() {
        val builderMemo: BuilderMemo<Int, String> = BuilderMemo()
        val treeBuilder: AvlTreeBuilder<Int, String> = AvlTreeBuilder(builderMemo)
        for (i in 1..730) {
            // System.out.println("test " + i);
            treeBuilder.insertNode(Integer.valueOf(i)) { "Node $i" }
            val iCopyFinal = 1501 - i
            treeBuilder.insertNode(Integer.valueOf(iCopyFinal)) { "Node $iCopyFinal" }
            treeBuilder.check()
        }
        val tree: AvlTree<Int, String> = treeBuilder.build()

        val node413Copy: INode<Int, String>? = tree[413]
        Assertions.assertNotNull(node413Copy)
        Assertions.assertEquals(413, node413Copy?.key)
        Assertions.assertEquals("Node 413", node413Copy?.data?.invoke())
        val unknownNode: INode<Int, String>? = tree[2700]
        Assertions.assertNull(unknownNode)
    }

    @Test
    fun fileTest() {
        val url = Thread.currentThread().contextClassLoader.getResource(".")
        val fic = File(url?.path, "File2.txt")
        if (fic.exists()) {
            fic.delete()
        }
        fic.createNewFile()
        val rf = RandomAccessFile(fic, "rw")

        val nodeFile: NodeFile<Int, String> = NodeFile(rf, SerializerInteger, SerializerString)

        // check build of avl tree
        val treeBuilder: AvlTreeBuilder<Int, String> = AvlTreeBuilder(BuilderFile(nodeFile))
        for (i in 1..1750) {
            treeBuilder.insertNode(Integer.valueOf(i)) { "Node $i" }
            val iCopyFinal = 3507 - i
            treeBuilder.insertNode(Integer.valueOf(iCopyFinal)) { "Node $iCopyFinal" }
        }
        treeBuilder.check()
        val tree = treeBuilder.build()

        // check Avl tree
        val node413Copy = tree[413]
        Assertions.assertNotNull(node413Copy)
        Assertions.assertEquals(413, node413Copy?.key)
        Assertions.assertEquals("Node 413", node413Copy?.data?.invoke())
        val unknownNode = tree[5700]
        Assertions.assertNull(unknownNode)
        val node3506 = tree[3506]
        Assertions.assertEquals("Node 3506", node3506?.data?.invoke())
        val node1503 = tree[1503]
        Assertions.assertEquals("Node 1503", node1503?.data?.invoke())
        val node2800 = tree[2800]
        Assertions.assertEquals("Node 2800", node2800?.data?.invoke())

        // check reading existing file
        val rf2 = RandomAccessFile(fic, "rw")
        val nodeFile2: NodeFile<Int, String> = NodeFile(rf2, SerializerInteger, SerializerString)
        val fileBuilder2: BuilderFile<Int, String> = BuilderFile(nodeFile2)
        val treeBuilder2: AvlTreeBuilder<Int, String> = AvlTreeBuilder(fileBuilder2)
        val tree2 = treeBuilder2.build()
        val node702Read = tree2[702]
        Assertions.assertNotNull(node702Read)
        Assertions.assertEquals(702, node702Read?.key)
        Assertions.assertEquals("Node 702", node702Read?.data?.invoke())
    }
}


