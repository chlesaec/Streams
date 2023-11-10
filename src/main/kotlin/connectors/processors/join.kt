package connectors.processors

import collections.map.AvlTree
import collections.map.AvlTreeBuilder
import collections.map.file.BuilderFile
import collections.map.file.NodeFile
import collections.map.file.SerializerString
import configuration.Config
import connectors.*
import connectors.commons.ItemManager
import connectors.io.InputRecord
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile

val joinConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("key", StringType())
            .add("lookup", PredecessorType("lookup"))
            .build()
    )
)

object JoinDescriptor:
        ConnectorDesc(
            VersionedIdentifier("Join", Version(listOf(1))),
            LinkInput(arrayOf(CSVRecord::class)),
            LinkOutput().add("main", CSVRecord::class),
            joinConfigDescription,
            { findImage("join.png") },
            { j: JobConnectorData, c : Config -> JoinConnector(c) }
        )
{
    init {
        Connectors.register(this)
    }
}

class JoinedRecord(val main: Any, val joined: Any)


class JoinConnector(config : Config) : Connector(config) {

    var treeBuilder: AvlTreeBuilder<String, String>? = null

    var tree: AvlTree<String, String>? = null

    val keyField = config.values["key"]

    override fun run(item: InputItem, output: OutputFunction) {
        if (item.linkOrigin?.endName ?: "" == "lookup") {
            this.treatLookup(item)
        }
        else {
            if (this.tree == null) {
                this.tree = this.treeBuilder?.build()
            }
            val completedItem = this.completeItem(item)
            if (completedItem != null) {
                output("main", completedItem)
            }
        }
    }

    private fun treatLookup(lookupItem: InputItem) {
        if (lookupItem.input != null) {
            if (keyField != null) {
                val value = ItemManager.getValue(lookupItem.input, keyField)
                if (value != null) {
                    this.treeBuilder?.insertNode(value.toString(), lookupItem.input::toString)
                }
            }
        }
    }

    private fun completeItem(item: InputItem) : Any? {
        if (keyField != null && item.input != null) {
            val value = ItemManager.getValue(item.input, keyField)
            if (value is String) {
                val node = this.tree?.get(value)
                if (node != null) {
                    return JoinedRecord(item.input, node.data())
                }
            }
        }
        return null
    }

    override fun initialize(jobData: JobConnectorData) {
        super.initialize(jobData)

        val folder = File(jobData.jobConfig.rootFolder.toFile(), "join/${jobData.identifier}")
        if (folder.exists()) {
            folder.delete()
        }
        folder.mkdirs()
        val treeFile = File(folder, "treeFile.avl")
        if (treeFile.exists()) {
            treeFile.delete()
        }
        treeFile.createNewFile()
        val rf = RandomAccessFile(treeFile, "rw")
        val nodeFile: NodeFile<String, String> = NodeFile(rf, SerializerString, SerializerString)

        this.treeBuilder = AvlTreeBuilder(BuilderFile(nodeFile))
    }

    override fun inputPriority(): List<String> {
        return listOf("lookup", "*")
    }
}
