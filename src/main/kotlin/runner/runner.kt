package runner

import connectors.Connector
import functions.InputItem
import graph.Edge
import graph.Node
import job.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.collections.HashMap

interface Runner {
    fun compile(job: Job): () -> Unit
}

class EndRunner(val identifier: UUID)

class LinkedRunnerItem(
    val link: JobLink?,
    val nextRunner: ConnectorRunner
)

class ConnectorRunner(
    val connector: Connector,
    val data: JobConnectorData,
    val nexts: List<LinkedRunnerItem>
) {
    var inputQueues: Queues<InputItem>? = null

    private val identifier: UUID = UUID.randomUUID()

    val predecessors = mutableListOf<ConnectorRunner>()

    fun sendTo(from: JobLinkData?, element: Any?) {
        inputQueues?.send(
            from?.endName ?: "*",
            InputItem(this.data, from, element)
        )
    }

    fun initialize() {
        this.nexts.forEach {
            it.nextRunner.predecessors.add(this)
        }
    }

    fun postInitialize() {
        this.inputQueues = QueuesBuilder<InputItem>(
            isEndProducer = { it.input is EndRunner },
            onEnd = { this.onEndRunner() },
            namesByPriority = this.connector.inputPriority(),
            producerCounter = { 1 }
        ).build()

        GlobalScope.launch {
            this@ConnectorRunner.consumeChannel()
        }
    }

    private fun onEndRunner() {
        this.connector.end()

        val terminate = EndRunner(this@ConnectorRunner.identifier)
        this@ConnectorRunner.nexts.forEach { edge: LinkedRunnerItem ->
            edge.link?.onEvent(EndEvent)
            edge.nextRunner.sendTo(edge.link?.data, terminate)
        }
    }

    fun isTerminated(): Boolean {
        return this@ConnectorRunner.inputQueues?.isTerminated() ?: true
    }

    private suspend fun consumeChannel() {
        GlobalScope.launch {
            while (!isTerminated()) {

                this@ConnectorRunner.inputQueues?.forEach { queueOrigin: String, element: InputItem ->
                    if (!(element.input is EndRunner)) {
                        connector.run(element) { branch: String, generatedElement: Any? ->
                            this@ConnectorRunner.sendNext(generatedElement)
                        }
                    }
                }
                if (!isTerminated()) {
                    delay(30)
                }
            }
        }
    }

    private fun sendNext(generatedElement: Any?) {
        runBlocking {
            launch {
                nexts.forEach {
                    it.link?.onEvent(ItemEvent)
                    it.nextRunner.sendTo(it.link?.data, generatedElement)
                }
            }
        }
    }
}

class RunableJob(
    val startNodes: Collection<ConnectorRunner>,
    val allNodes: Collection<ConnectorRunner>
) {
    fun execute() {
        runBlocking {
            val identifier = UUID.randomUUID()

            startNodes.forEach {
                GlobalScope.launch {
                    it.sendTo(null, null)
                    it.sendTo(null, EndRunner(identifier))
                }
            }
            while (allNodes.filter { !it.isTerminated() }.isNotEmpty()) {
                delay(300)
            }
        }
    }
}

class JobRunner() : Runner {
    override fun compile(job: Job): () -> Unit {
        val endNodes = job.graph.endNodes()
        val connectorRunners = this.buildRunners(endNodes)

        val startNodes = job.graph.startNodes().map { connectorRunners[it.identifier]!! }

        return RunableJob(startNodes, connectorRunners.values)::execute
    }


    private fun buildRunners(endNodes: List<Node<JobConnector, JobLink>>): Map<UUID, ConnectorRunner> {

        // scan graph from end to start in order to create ConnectorRunner before LinkedRunnerItem
        val runners = HashMap<UUID, ConnectorRunner>()
        val workEdges = mutableListOf<Edge<JobConnector, JobLink>>()

        endNodes.forEach { node: Node<JobConnector, JobLink> ->
            val c = ConnectorRunner(
                node.data.buildConnector(),
                node.data.connectorData,
                emptyList()
            )
            runners[node.identifier] = c
            node.precs.forEach(workEdges::add)
        }

        while (!workEdges.isEmpty()) {
            var index = 0
            while (index < workEdges.size) {
                val edge = workEdges[index]
                val allNextReady = edge.start.nexts.all { runners.containsKey(it.end.identifier) }
                if (allNextReady) {
                    val nexts = edge.start.nexts.map {
                        val connectorRunner = runners[it.end.identifier]
                        LinkedRunnerItem(it.data, connectorRunner!!)
                    }
                    val c = ConnectorRunner(
                        edge.start.data.buildConnector(),
                        edge.start.data.connectorData,
                        nexts
                    )
                    runners[edge.start.identifier] = c

                    // update workEdges
                    edge.start.precs.forEach(workEdges::add)
                    workEdges.removeAt(index)
                } else {
                    // Not all ready, to next working edge.
                    index++
                }
            }

        }

        return runners
    }
}
